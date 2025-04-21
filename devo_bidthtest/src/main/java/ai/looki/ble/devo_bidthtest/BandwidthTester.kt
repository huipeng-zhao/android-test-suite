package ai.looki.ble.devo_bidthtest

import ai.looki.ble.common.Constants.DEVO_TAG
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * DEVO端带宽统计器（统计上行发送/下行接收）
 */
class BandwidthTester {
    private val TAG = DEVO_TAG + BandwidthTester::class.java.simpleName

    // 上行（DEVO→手机）
    private var uplinkStartTime = 0L
    private var uplinkTotalBytes = 0L
    private var uplinkMbps = 0f

    // 下行（手机→DEVO）
    private var downlinkStartTime = 0L
    private var downlinkTotalBytes = 0L
    private var downlinkMbps = 0f

    private var updateJob: Job? = null
    private var bandwidthCallback: ((Float, Float) -> Unit)? = null

    fun getUplinkMbps(): Float = uplinkMbps
    fun getDownlinkMbps(): Float = downlinkMbps

    //--------------------------------------------------
    // 上行测试（发送数据）
    //--------------------------------------------------
    fun startUplink() {
        Log.d(TAG, "startUplink() called")
        uplinkStartTime = System.currentTimeMillis()
        uplinkTotalBytes = 0
        startUpdateLoop()
    }

    fun addUplinkBytes(bytes: Int) {
        Log.d(TAG, "addUplinkBytes() called")
        uplinkTotalBytes += bytes
    }

    //--------------------------------------------------
    // 下行测试（接收数据）
    //--------------------------------------------------
    fun startDownlink() {
        Log.d(TAG, "startDownlink() called")
        downlinkStartTime = System.currentTimeMillis()
        downlinkTotalBytes = 0
        startUpdateLoop()
    }

    fun addDownlinkBytes(bytes: Int) {
        Log.d(TAG, "addDownlinkBytes() called")
        downlinkTotalBytes += bytes
    }

    //--------------------------------------------------
    // 停止测试
    //--------------------------------------------------
    fun stop() {
        Log.d(TAG, "stop() called")
        updateJob?.cancel()
//        uplinkMbps = 0f
//        downlinkMbps = 0f
    }

    //--------------------------------------------------
    // 实时更新带宽数据（每秒触发回调）
    //--------------------------------------------------
    fun setUpdateCallback(callback: (Float, Float) -> Unit) {
        Log.d(TAG, "setUpdateCallback() called")
        bandwidthCallback = callback
    }

    private fun startUpdateLoop() {
        Log.d(TAG, "startUpdateLoop() called")
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Default).launch {
            while (true) {
                delay(1000)
                calculateBandwidth()
                bandwidthCallback?.invoke(uplinkMbps, downlinkMbps)
            }
        }
    }

    private fun calculateBandwidth() {
        Log.d(TAG, "calculateBandwidth() called")
        // 上行带宽计算
        if (uplinkStartTime > 0) {
            val durationSec = (System.currentTimeMillis() - uplinkStartTime) / 1000f
            uplinkMbps = (uplinkTotalBytes * 8) / (durationSec * 1_000_000)
        }

        // 下行带宽计算
        if (downlinkStartTime > 0) {
            val durationSec = (System.currentTimeMillis() - downlinkStartTime) / 1000f
            downlinkMbps = (downlinkTotalBytes * 8) / (durationSec * 1_000_000)
        }
    }
}