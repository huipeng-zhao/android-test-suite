package ai.looki.ble.app_test

import ai.looki.ble.common.Constants.APP_TAG
import ai.looki.ble.common.Constants.CCCD
import ai.looki.ble.common.Constants.SERVICE_UUID
import ai.looki.ble.common.Constants.CHAR_CONTROL_UUID
import ai.looki.ble.common.Constants.CHAR_UPLINK_UUID
import ai.looki.ble.common.Constants.CHAR_DOWNLINK_UUID
import ai.looki.ble.common.Constants.CMD_START_UPLINK
import ai.looki.ble.common.Constants.CMD_START_DOWNLINK
import ai.looki.ble.common.Constants.CMD_STOP_DOWNLINK
import ai.looki.ble.common.Constants.CMD_STOP_UPLINK
import ai.looki.ble.common.Constants.DEFAULT_MTU
import ai.looki.ble.common.Constants.TEST_DATA_BYTE
import ai.looki.ble.common.DirectionalBandwidthTester
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanFilter
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import java.util.UUID

class BleCentralManager(private val context: Context) {
    companion object {
        private val TAG = APP_TAG + BleCentralManager::class.java.simpleName

        private val UUID_DESC_CCCD: UUID = UUID.fromString(CCCD)

        private val UUID_SEVRICE_UUID = UUID.fromString(SERVICE_UUID)
        private val CONTROL_UUID = UUID.fromString(CHAR_CONTROL_UUID)
        private val DOWNLINK_UUID = UUID.fromString(CHAR_DOWNLINK_UUID)
        private val UPLINK_UUID = UUID.fromString(CHAR_UPLINK_UUID)
        // 初始化 BLE 核心组件
        private val bluetoothAdapter: BluetoothAdapter by lazy {
            BluetoothAdapter.getDefaultAdapter().also {
                require(it != null) { "设备不支持蓝牙" }
            }
        }
    }

    // —— 新增状态回调 ——
    private var statusCallback: ((String)->Unit)? = null

    /** 注册服务发现状态回调 */
    fun setStatusCallback(cb: (String)->Unit) {
        statusCallback = cb
    }

    // —— 新增：单向回调 ——
    private var uplinkCallback: ((Double) -> Unit)? = null    // 只回传上行（Rx）
    private var downlinkCallback: ((Double) -> Unit)? = null  // 只回传下行（Tx）

    /** 注册上行（DEVO→手机）带宽回调 */
    fun setUplinkCallback(cb: (Double)->Unit) {
        uplinkCallback = cb
    }

    /** 注册下行（手机→DEVO）带宽回调 */
    fun setDownlinkCallback(cb: (Double)->Unit) {
        downlinkCallback = cb
    }

    private val uplinkTester   = DirectionalBandwidthTester()
    private val downlinkTester = DirectionalBandwidthTester()

    // GATT 特征声明
    private var controlChar: BluetoothGattCharacteristic? = null
    private var uplinkChar: BluetoothGattCharacteristic? = null
    private var downlinkChar: BluetoothGattCharacteristic? = null

    // 协程作用域（用于异步操作）
    private val coroutineScope = CoroutineScope(Dispatchers.Main + Job())

    private var gatt: BluetoothGatt? = null
    private var isTestingDownlink = false
    private val bandwidthTester = BandwidthTester()
    private var bandwidthCallback: ((Double, Double) -> Unit)? = null // 回调变量类型与使用处保持一致（Double）

    // 新增：公开的回调设置方法
    fun setBandwidthCallback(callback: (Double, Double) -> Unit) {
        Log.d(TAG, "setBandwidthCallback() called")
        bandwidthCallback = callback

        // *** 新增：把 callback 传给 bandwidthTester ***
        bandwidthTester.setUpdateCallback { uplinkF, downlinkF ->
            // Float -> Double
            callback(uplinkF.toDouble(), downlinkF.toDouble())
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    fun startScan() {
        Log.d(TAG, "startScan() called")
        val scanner = bluetoothAdapter.bluetoothLeScanner
        val filter = ScanFilter.Builder().setServiceUuid(ParcelUuid(UUID_SEVRICE_UUID)).build()
        scanner.startScan(listOf(filter), ScanSettings.Builder().build(), scanCallback)
    }

    private val scanCallback: ScanCallback = object : ScanCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            Log.d(TAG, "onScanResult(): $result")
            result?.device?.connectGatt(context, false, gattCallback)
        }
    }

    private val gattCallback = object : BluetoothGattCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            when (newState) {
                BluetoothProfile.STATE_CONNECTED -> {
                    Log.d(TAG, "已连接，正在发现服务...")
                    val scanner = bluetoothAdapter.bluetoothLeScanner
                    this@BleCentralManager.gatt = gatt
                    scanner.stopScan(scanCallback)
                    // 请求使用 2M PHY
                    gatt.setPreferredPhy(
                        BluetoothDevice.PHY_LE_2M_MASK,
                        BluetoothDevice.PHY_LE_2M_MASK,
                        BluetoothDevice.PHY_OPTION_NO_PREFERRED
                    )
                    gatt.discoverServices()
                    // gatt.requestMtu(DEFAULT_MTU)
                }
                BluetoothProfile.STATE_DISCONNECTED -> {
                    Log.d(TAG, "连接断开，status: $status")
                    stopTest()
                }
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onPhyUpdate(
            gatt: BluetoothGatt,
            txPhy: Int, rxPhy: Int, status: Int
        ) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "PHY 已更新：tx=$txPhy, rx=$rxPhy")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                // 再次保存
                this@BleCentralManager.gatt = gatt
                Log.d(TAG, "服务发现成功")
                statusCallback?.invoke("状态：服务发现成功，请确认 DEVO 端")

                val service = gatt.getService(UUID_SEVRICE_UUID)
                controlChar = service.getCharacteristic(CONTROL_UUID)
                uplinkChar = service.getCharacteristic(UPLINK_UUID)
                downlinkChar = service.getCharacteristic(DOWNLINK_UUID)

                // 启用上行特征通知（接收 DEVO 端数据）
                enableNotifications(uplinkChar)
            } else {
                Log.d(TAG, "服务发现失败，status = $status")
                statusCallback?.invoke("状态：服务发现失败 (code=$status)，请重新尝试")
            }
        }

//        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
//            if (status == BluetoothGatt.GATT_SUCCESS) {
//                negotiatedMtu = mtu
//                Log.d(TAG, "MTU 协商成功: $mtu")
//            } else {
//                Log.d(TAG, "MTU 协商失败，status = $status")
//            }
//        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            if (characteristic.uuid == UPLINK_UUID) {
                Log.d(TAG, "onCharacteristicChanged(): characteristic.uuid: " + characteristic.uuid)
                uplinkTester.addBytes(characteristic.value.size)
            } else{
                Log.d(TAG, "onCharacteristicChanged(): Not UPLINK_UUID, characteristic.uuid: " + characteristic.uuid)
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            // 当 STOP 指令写入完成后，再断开
            if (characteristic.uuid.toString()==CHAR_CONTROL_UUID)
                if ((characteristic.value.getOrNull(0)== CMD_STOP_UPLINK) || (characteristic.value.getOrNull(0)== CMD_STOP_DOWNLINK))  {
                gatt.disconnect()
                gatt.close()
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun enableNotifications(characteristic: BluetoothGattCharacteristic?) {
        Log.d(TAG, "enableNotifications(): called")
        characteristic?.let { char ->
            // 1. 设置本地特征通知
            gatt?.setCharacteristicNotification(char, true)

            // 2. 写入 CCCD 描述符以启用远程通知
            val descriptor = char.getDescriptor(UUID_DESC_CCCD)
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            gatt?.writeDescriptor(descriptor)
        }
    }

    // 带宽测试控制方法
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startUplinkTest() {
        uplinkTester.start()
        uplinkTester.setCallback { mbps -> uplinkCallback?.invoke(mbps.toDouble()) }

        controlChar?.value = byteArrayOf(CMD_START_UPLINK)
        gatt?.writeCharacteristic(controlChar)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun startDownlinkTest() {
        downlinkTester.start()
        downlinkTester.setCallback { mbps -> downlinkCallback?.invoke(mbps.toDouble()) }

        controlChar?.value = byteArrayOf(CMD_START_DOWNLINK)
        gatt?.writeCharacteristic(controlChar)

        isTestingDownlink = true
        coroutineScope.launch(Dispatchers.IO) {
            val chunk = ByteArray(DEFAULT_MTU-3){ TEST_DATA_BYTE }
            while (isTestingDownlink) {
                downlinkChar?.apply { writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE; value = chunk }
                gatt?.writeCharacteristic(downlinkChar)
                downlinkTester.addBytes(chunk.size)
                delay(1) // or use 1 or more
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stopUplinkTest() {
        Log.d(TAG, "stopTest(): called")
        uplinkTester.stop()
        controlChar?.value = byteArrayOf(CMD_STOP_UPLINK)
        gatt?.writeCharacteristic(controlChar)
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stopDownlinkTest() {
        Log.d(TAG, "stopTest(): called")
        isTestingDownlink = false
        downlinkTester.stop()
        controlChar?.value = byteArrayOf(CMD_STOP_DOWNLINK)
        gatt?.writeCharacteristic(controlChar)
    }

    // 手机端 BleCentralManager.kt
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stopTest() {
        Log.d(TAG, "stopTest(): called")
        stopUplinkTest()
        stopDownlinkTest()

        // 主动断开连接并释放资源
//        gatt?.run {
//            disconnect()
//            close()
//        }
//        gatt = null

        // 停止所有测试
//        coroutineScope.cancel()
//        bandwidthTester.stop()
    }
}