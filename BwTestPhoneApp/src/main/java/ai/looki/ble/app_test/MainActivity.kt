package ai.looki.ble.app_test

import ai.looki.ble.app_test.databinding.ActivityMainBinding
import ai.looki.ble.common.Constants.APP_TAG
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
import androidx.appcompat.app.AlertDialog
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.core.content.ContextCompat
import androidx.core.net.toUri

class MainActivity : ComponentActivity() {
    companion object {
        private val TAG = APP_TAG + MainActivity::class.java.simpleName

        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        )

    }

    private lateinit var binding: ActivityMainBinding
    private lateinit var bleManager: BleCentralManager

    private var uplinkActive   = false
    private var downlinkActive = false

    private val uplinkBtnDebounceMs = 5000L // btn等待时长：5秒

    @SuppressLint("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        Log.d(TAG, "onCreate() called")
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // 首次启动时检查所有必要权限
        checkPermissions()

        bleManager = BleCentralManager(this).apply {
            setStatusCallback { status ->
                runOnUiThread {
                    binding.tvServiceStatus.text = status
                }
            }

            setUplinkCallback { rxMbps ->
                runOnUiThread {
                    binding.tvUplink.text = "Rx = ${"%.3f".format(rxMbps)} Mbps [ ${"%.1f".format(rxMbps*125)} KB/s ]"
                }
            }
            setDownlinkCallback { txMbps ->
                runOnUiThread {
                    binding.tvDownlink.text = "Tx = ${"%.3f".format(txMbps)} Mbps [ ${"%.1f".format(txMbps*125)} KB/s ]"
                }
            }
        }

        binding.btnScan.setOnClickListener { bleManager.startScan() }

        // —— 接收数据按钮（上行） ——
        binding.btnTestUplink.setOnClickListener {
//            bleManager.stopScan()
//            bleManager.restartScan()
            uplinkActive = !uplinkActive   // 切换状态
            updateBtnStyle(binding.btnTestUplink, uplinkActive)

            binding.btnTestUplink.isEnabled = false
            binding.btnTestDownlink.isEnabled = false
            binding.btnTestUplink.text = "接收中...不可点击"
            binding.btnTestDownlink.text = "防触碰...不可点击"

            // 5 秒后恢复按钮
            binding.btnTestUplink.postDelayed({
                binding.btnTestUplink.isEnabled = true
                binding.btnTestUplink.text = "停止接收"
            }, uplinkBtnDebounceMs)
            if (uplinkActive) {
                // 启动上行测试
                bleManager.startUplinkTest()
                // 如果此时 downlink 也在运行，则先停下
                if (downlinkActive) {
                    downlinkActive = false
                    updateBtnStyle(binding.btnTestDownlink, false)
                    bleManager.stopDownlinkTest()
                }
            } else {
                // 停止上行：按钮动画+文案
                binding.btnTestUplink.isEnabled = false
                binding.btnTestUplink.text = "停止中...不可点击"
                binding.btnTestDownlink.text = "防触碰...不可点击"
                bleManager.stopUplinkTest()

                // 5 秒后恢复按钮
                binding.btnTestUplink.postDelayed({
                    binding.btnTestUplink.isEnabled = true
                    binding.btnTestDownlink.isEnabled = true
                    binding.btnTestUplink.text = "接收数据"
                    binding.btnTestDownlink.text = "发送数据"
                    updateBtnStyle(binding.btnTestUplink, false)
                }, uplinkBtnDebounceMs)
            }
        }

        // —— 发送数据按钮（下行） ——
        binding.btnTestDownlink.setOnClickListener {
//            bleManager.stopScan()
//            bleManager.restartScan()
            downlinkActive = !downlinkActive
            updateBtnStyle(binding.btnTestDownlink, downlinkActive)

            binding.btnTestDownlink.isEnabled = false
            binding.btnTestUplink.isEnabled = false
            binding.btnTestDownlink.text = "发送中...不可点击"
            binding.btnTestUplink.text = "防触碰...不可点击"

            // 5 秒后恢复按钮
            binding.btnTestDownlink.postDelayed({
                binding.btnTestDownlink.isEnabled = true
                binding.btnTestDownlink.text = "停止发送"
            }, uplinkBtnDebounceMs)
            if (downlinkActive) {
                bleManager.startDownlinkTest()
                if (uplinkActive) {
                    uplinkActive = false
                    updateBtnStyle(binding.btnTestUplink, false)
                    bleManager.stopUplinkTest()
                }
            } else {
                binding.btnTestDownlink.isEnabled = false
                binding.btnTestDownlink.text = "停止中...不可点击"
                binding.btnTestUplink.text = "防触碰...不可点击"
                bleManager.stopDownlinkTest()

                // 5 秒后恢复按钮
                binding.btnTestDownlink.postDelayed({
                    binding.btnTestDownlink.isEnabled = true
                    binding.btnTestUplink.isEnabled = true
                    binding.btnTestDownlink.text = "发送数据"
                    binding.btnTestUplink.text = "接收数据"
                    updateBtnStyle(binding.btnTestDownlink, false)
                }, uplinkBtnDebounceMs)
            }
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    @SuppressLint("SetTextI18n")
    override fun onStop() {
        Log.d(TAG, "onStop() called!")
        super.onStop()
        bleManager.stopTest()
        bleManager.stopScan()
        finish()
    }

    /** 根据 active 状态设置按钮背景：true = 蓝色，false = 灰色 */
    private fun updateBtnStyle(button: android.widget.Button, active: Boolean) {
        if (active) {
            button.setBackgroundColor(Color.Blue.toArgb())
            button.setTextColor(Color.White.toArgb())
        } else {
            button.setBackgroundColor(Color.LightGray.toArgb())
            button.setTextColor(Color.Black.toArgb())
        }
    }

    // 权限请求结果处理器
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }
        if (allGranted) {
            Log.d(TAG, "All requested permissions granted!")
        } else {
            // 记录具体被拒绝的权限
            val denied = permissions.filter { !it.value }.keys
            Log.w(TAG, "Denied permissions: ${denied.joinToString()}")
            showPermissionDeniedDialog()
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

    private fun checkPermissions() {
        Log.d(TAG, "checkPermissions() called")
        val deniedPermissions = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (deniedPermissions.isEmpty()) {
            Log.d(TAG, "All required permissions granted!")
        } else {
            // 记录具体缺失的权限
            Log.w(TAG, "Missing permissions: ${deniedPermissions.joinToString()}")
            // 请求缺失的权限
            requestPermissionLauncher.launch(deniedPermissions.toTypedArray())
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
}