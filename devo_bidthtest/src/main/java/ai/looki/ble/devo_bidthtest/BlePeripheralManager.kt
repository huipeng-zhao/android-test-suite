package ai.looki.ble.devo_bidthtest

import ai.looki.ble.common.Constants.CCCD
import ai.looki.ble.common.Constants.CHAR_CONTROL_UUID
import ai.looki.ble.common.Constants.CHAR_DOWNLINK_UUID
import ai.looki.ble.common.Constants.CHAR_UPLINK_UUID
import ai.looki.ble.common.Constants.CMD_START_DOWNLINK
import ai.looki.ble.common.Constants.CMD_START_UPLINK
import ai.looki.ble.common.Constants.CMD_STOP_DOWNLINK
import ai.looki.ble.common.Constants.CMD_STOP_UPLINK
import ai.looki.ble.common.Constants.DEFAULT_MTU
import ai.looki.ble.common.Constants.DEVO_TAG
import ai.looki.ble.common.Constants.SERVICE_UUID
import ai.looki.ble.common.Constants.TEST_DATA_BYTE
import ai.looki.ble.common.DirectionalBandwidthTester
import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGatt.GATT_SUCCESS
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothGattServer
import android.bluetooth.BluetoothGattServerCallback
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothGattService.SERVICE_TYPE_PRIMARY
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.le.AdvertiseCallback
import android.bluetooth.le.AdvertiseData
import android.bluetooth.le.AdvertiseSettings
import android.bluetooth.le.BluetoothLeAdvertiser
import android.content.Context
import android.os.ParcelUuid
import android.util.Log
import androidx.annotation.RequiresPermission
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.UUID

class BlePeripheralManager(
    private val context: Context,
) {
    var gattServer: BluetoothGattServer? = null
    private var connectedDevice: BluetoothDevice? = null
    private var uplinkChar: BluetoothGattCharacteristic? = null
    private var downlinkChar: BluetoothGattCharacteristic? = null
    private var isUplinkActive = false
    private var uplinkJob: Job? = null
    private var updateJob: Job? = null
    private var bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
    private var advertiser: BluetoothLeAdvertiser? = bluetoothAdapter.bluetoothLeAdvertiser

    private val uplinkTester   = DirectionalBandwidthTester()
    private val downlinkTester = DirectionalBandwidthTester()
    // 两个单向回调
    private var uplinkCallback: ((Double)->Unit)? = null
    private var downlinkCallback: ((Double)->Unit)? = null

    /** 注册上行（DEVO→手机）带宽回调 */
    fun setUplinkCallback(cb: (Double)->Unit) {
        uplinkCallback = cb
    }

    /** 注册下行（手机→DEVO）带宽回调 */
    fun setDownlinkCallback(cb: (Double)->Unit) {
        downlinkCallback = cb
    }

    // —— 新增 Descriptor 写入状态回调 ——
    private var descriptorStatusCallback: ((String) -> Unit)? = null

    /** 注册 CCCD 写入状态回调 */
    fun setDescriptorStatusCallback(cb: (String) -> Unit) {
        descriptorStatusCallback = cb
    }

    companion object {
        private val TAG = DEVO_TAG + BlePeripheralManager::class.java.simpleName

        private val CONTROL_UUID = UUID.fromString(CHAR_CONTROL_UUID)
        private val DOWNLINK_UUID = UUID.fromString(CHAR_DOWNLINK_UUID)
        private val UPLINK_UUID = UUID.fromString(CHAR_UPLINK_UUID)
    }

    private val bluetoothManager: BluetoothManager by lazy {
        context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
    }

    //--------------------------------------------------
    // 启动BLE广播与服务
    //--------------------------------------------------
    // 初始化GATT服务
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun startAdvertising() {
        Log.d(TAG, "启动 BLE 广播")
        if (!bluetoothAdapter.isMultipleAdvertisementSupported) {
            Log.e(TAG, "设备不支持 BLE 外设模式")
        }

        // 1. 配置广播参数
        val settings = AdvertiseSettings.Builder()
            .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_LOW_LATENCY) // 低延迟模式
            .setConnectable(true) // 允许连接
            .setTimeout(0) // 无超时限制
            .build()

        // 2. 配置广播数据（包含服务 UUID）
        val data = AdvertiseData.Builder()
            .setIncludeDeviceName(false) // 不广播设备名（避免隐私问题）
            .addServiceUuid(ParcelUuid(UUID.fromString(SERVICE_UUID))) // 指定服务 UUID
            .build()

        // 3. 开始广播
        advertiser?.startAdvertising(settings, data, advertiseCallback)
    }

    //--------------------------------------------------
    // 停止广播
    //--------------------------------------------------
    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    fun stopAdvertising() {
        advertiser?.stopAdvertising(advertiseCallback)
    }

    //--------------------------------------------------
    // 广播状态回调
    //--------------------------------------------------
    private val advertiseCallback = object : AdvertiseCallback() {
        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onStartSuccess(settingsInEffect: AdvertiseSettings) {
            Log.d(TAG, "广播启动成功")
            // 初始化 gattServer
            gattServer = bluetoothManager.openGattServer(context, gattServerCallback)
            setupGattService() // 广播成功后初始化 GATT 服务
        }

        override fun onStartFailure(errorCode: Int) {
            Log.e(TAG, "广播启动失败，错误码: $errorCode")
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun setupGattService() {
        Log.d(TAG, "setupGattService() called")
        val service = BluetoothGattService(
            UUID.fromString(SERVICE_UUID),
            SERVICE_TYPE_PRIMARY
        )

        // 控制特征（接收手机指令）
        val controlChar = BluetoothGattCharacteristic(
            CONTROL_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
        service.addCharacteristic(controlChar)

        // 上行特征（DEVO→手机）
        uplinkChar = BluetoothGattCharacteristic(
            UPLINK_UUID,
            BluetoothGattCharacteristic.PROPERTY_NOTIFY,
            BluetoothGattCharacteristic.PERMISSION_READ
        )
        val cccd = BluetoothGattDescriptor(UUID.fromString(CCCD),
            BluetoothGattDescriptor.PERMISSION_READ or BluetoothGattDescriptor.PERMISSION_WRITE
        )
        if(uplinkChar == null) {
            Log.e(TAG, "uplinkChar is null!")
        }
        uplinkChar?.addDescriptor(cccd)
        service.addCharacteristic(uplinkChar)

        // 新增下行特征（手机→DEVO，需可写）
        downlinkChar = BluetoothGattCharacteristic(
            DOWNLINK_UUID,
            BluetoothGattCharacteristic.PROPERTY_WRITE,
            BluetoothGattCharacteristic.PERMISSION_WRITE
        )
//        ).apply {
//            // —— 新增：无响应写入 ——
//            writeType = BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
//        }
        service.addCharacteristic(downlinkChar)

        gattServer?.addService(service)
    }

    //--------------------------------------------------
    // 停止所有服务
    //--------------------------------------------------
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    fun stop() {
        Log.d(TAG, "stop() called")
        updateJob?.cancel()
        uplinkJob?.cancel()
        isUplinkActive = false
        gattServer?.close()
        gattServer = null
        connectedDevice = null
        uplinkTester.stop()
        downlinkTester.stop()
    }

    //--------------------------------------------------
    // GATT服务回调
    //--------------------------------------------------
    private val gattServerCallback = object : BluetoothGattServerCallback() {
        override fun onConnectionStateChange(device: BluetoothDevice?, status: Int, newState: Int) {
            Log.d(
                TAG,
                "连接状态变化: device=${device?.address}, newState=$newState, status=$status"
            )
            if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                Log.d(TAG, "onConnectionStateChange() 设备断开时停止测试")
                stopCurrentTest() // 设备断开时停止测试
            }
            Log.d(TAG, "onConnectionStateChange() called")
            connectedDevice = device
        }

        override fun onServiceAdded(status: Int, service: BluetoothGattService?) {
            Log.d(TAG, "onServiceAdded: status=$status, service=${service?.uuid}")
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onDescriptorWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            descriptor: BluetoothGattDescriptor,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            Log.d(TAG, "onDescriptorWriteRequest(): uuid=${descriptor.uuid}, value=${value?.contentToString()}")
            // 只处理 CCCD
            if (descriptor.uuid.toString().equals("00002902-0000-1000-8000-00805f9b34fb", true)) {
                // 存储客户端写入的 value（ENABLE_NOTIFICATION 或 DISABLE）
                descriptor.value = value
                // 响应成功
                gattServer?.sendResponse(device, requestId,
                    GATT_SUCCESS, 0, null)
                Log.d(TAG, "CCCD 写入成功，已开启/关闭通知")
                descriptorStatusCallback?.invoke("CCCD 写入成功，通知：已开启通知")
            } else {
                gattServer?.sendResponse(device, requestId,
                    BluetoothGatt.GATT_FAILURE, 0, null)
                descriptorStatusCallback?.invoke("CCCD 写入失败，通知：开启/关闭失败")
            }
        }

        @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
        override fun onCharacteristicWriteRequest(
            device: BluetoothDevice?,
            requestId: Int,
            characteristic: BluetoothGattCharacteristic,
            preparedWrite: Boolean,
            responseNeeded: Boolean,
            offset: Int,
            value: ByteArray?
        ) {
            when (characteristic.uuid) {
                CONTROL_UUID -> {
                    when (value?.getOrNull(0)) {
                        CMD_START_UPLINK -> startUplinkTest()
                        CMD_START_DOWNLINK -> startDownlinkTest()
                        CMD_STOP_UPLINK -> stopUpLink() // 新增停止逻辑
                        CMD_STOP_DOWNLINK -> stopDownLink() // 新增停止逻辑
                    }
                    gattServer?.sendResponse(device, requestId, GATT_SUCCESS, 0, null)
                }
                // 新增：处理下行数据写入（手机 → DEVO）
                DOWNLINK_UUID -> {
                    val dataSize = value?.size ?: 0
                    downlinkTester.addBytes(dataSize) // 统计下行数据
                    gattServer?.sendResponse(device, requestId, GATT_SUCCESS, 0, null)
                }
            }
        }

        // MTU 变更回调
//        override fun onMtuChanged(device: BluetoothDevice?, mtu: Int) {
//            negotiatedMtu = mtu
//            Log.d(TAG, "MTU 更新为: $mtu")
//            // TODO: 可以在此调整发送数据块的大小
//        }

    }

    private fun stopUpLink() {
        Log.d(TAG, "stopTest(): called")
        isUplinkActive = false
        uplinkJob?.cancel()
        uplinkTester.stop()
    }

    private fun stopDownLink() {
        Log.d(TAG, "stopTest(): called")
        downlinkTester.stop()
    }

    // 新增停止方法
    private fun stopCurrentTest() {
        Log.d(TAG, "stopCurrentTest called")
        stopUpLink()
        stopDownLink()
    }

    //--------------------------------------------------
    // 上行测试逻辑
    //--------------------------------------------------
    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun startUplinkTest() {
        uplinkTester.start()
        // 回调给 UI (txPhy->rxPhy 顺序)
        uplinkTester.setCallback { mbps -> uplinkCallback?.invoke(mbps.toDouble()) } // TODO: or use downlink

        // 循环发送 Notify
        isUplinkActive = true
        uplinkJob = CoroutineScope(Dispatchers.IO).launch {
            val chunk = ByteArray(DEFAULT_MTU - 3) { TEST_DATA_BYTE }
            while (isUplinkActive && isActive) {
                uplinkChar?.let {
                    it.value = chunk
                    gattServer?.notifyCharacteristicChanged(connectedDevice, it, false)
                    uplinkTester.addBytes(chunk.size)
                }
                delay(1) // TODO: or use 1 r more
            }
        }
    }

    private fun startDownlinkTest() {
        downlinkTester.start()
        downlinkTester.setCallback { mbps -> downlinkCallback?.invoke(mbps.toDouble()) } // TODO: or use uplink
    }
}