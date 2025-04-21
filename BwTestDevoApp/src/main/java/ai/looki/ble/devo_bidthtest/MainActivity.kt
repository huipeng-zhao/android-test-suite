package ai.looki.ble.devo_bidthtest

import ai.looki.ble.common.Constants.DEVO_TAG
import ai.looki.ble.devo_bidthtest.databinding.ActivityMainBinding
import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresPermission
import androidx.core.content.ContextCompat
import androidx.appcompat.app.AlertDialog
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    companion object {
        private val TAG = DEVO_TAG + MainActivity::class.java.simpleName

        private val REQUIRED_PERMISSIONS = arrayOf(
            // file
            Manifest.permission.WRITE_EXTERNAL_STORAGE,
            // wifi
            Manifest.permission.ACCESS_FINE_LOCATION,
        )
    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var bleManager: BlePeripheralManager

    private var descriptorStatus: String = "通知：未开启"

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called")
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 首次启动时检查所有必要权限
        checkPermissions()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_ADVERTISE)
    private fun initBlePeripheral() {
        Log.d(TAG, "initBlePeripheral() called")
//        bleManager = BlePeripheralManager(this).apply {
//            setBandwidthCallback { uplinkMbps, downlinkMbps ->
//                runOnUiThread {
//                    binding.tvUplinkSpeed.text = "Tx = ${"%.3f".format(uplinkMbps)} Mbps"
//                    binding.tvDownlinkSpeed.text = "Rx = ${"%.3f".format(downlinkMbps)} Mbps"
//                }
//            }
//            startAdvertising()
//        }
        bleManager = BlePeripheralManager(this).apply {
            setDescriptorStatusCallback { status ->
                runOnUiThread {
                    binding.tvDescriptorStatus.text = status
                }
            }

            setUplinkCallback { txMbps ->
                runOnUiThread {
                    binding.tvUplinkSpeed.text = "Tx = ${"%.3f".format(txMbps)} Mbps [ ${"%.3f".format(txMbps*125)} KB/s ]"
                }
            }
            setDownlinkCallback { rxMbps ->
                runOnUiThread {
                    binding.tvDownlinkSpeed.text = "Rx = ${"%.3f".format(rxMbps)} Mbps [ ${"%.3f".format(rxMbps*125)} KB/s ]"
                }
            }
            startAdvertising()
        }
    }

    // 权限请求结果处理器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            initBlePeripheral() // 全部权限通过后初始化BLE
        } else {
            showPermissionDeniedDialog()
        }
    }

    private fun checkPermissions() {
        Log.d(TAG, "checkPermissions() called")
        val deniedPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isEmpty()) {
            initBlePeripheral() // 所有权限已授予
        } else {
            // 请求所有未授权的权限
            requestPermissionLauncher.launch(deniedPermissions.toTypedArray())
        }
    }

    private fun showPermissionDeniedDialog() {
        Log.d(TAG, "showPermissionDeniedDialog() called")
        AlertDialog.Builder(this)
            .setTitle("权限缺失")
            .setMessage("必须允许所有权限才能进行蓝牙带宽测试")
            .setPositiveButton("设置") { _, _ ->
                // 导航到应用设置页面
                AppSettingsHelper.openAppSettings(this)
            }
            .setNegativeButton("取消") { dialog, _ ->
                dialog.dismiss()
                finish() // 关闭应用
            }
            .show()
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    override fun onDestroy() {
        Log.d(TAG, "onDestroy() called")
        if (::bleManager.isInitialized) {
            bleManager.stopAdvertising()
            bleManager.stop()
        }
        super.onDestroy()
    }
}

// 辅助工具类：跳转应用设置页面
object AppSettingsHelper {
    fun openAppSettings(context: ComponentActivity) {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            "package:${context.packageName}".toUri()
        )
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
        context.startActivity(intent)
    }
}