package ai.looki.ble.app_test

import ai.looki.ble.common.Constants.APP_TAG
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong

/**
 * 手机端带宽统计器（统计下行发送/上行接收）
 */
class BandwidthTester {
    companion object {
        private val TAG = APP_TAG + BandwidthTester::class.java.simpleName
    }

    // 下行（手机→DEVO）
    private var downlinkStartTime = 0L
    private var downlinkTotalBytes = AtomicLong(0)
    private var downlinkMbps = 0f

    // 上行（DEVO→手机）
    private var uplinkStartTime = 0L
    private var uplinkTotalBytes = AtomicLong(0)
    private var uplinkMbps = 0f

    private var updateJob: Job? = null
    private var bandwidthCallback: ((Float, Float) -> Unit)? = null

    //--------------------------------------------------
    // 下行测试（发送数据）
    //--------------------------------------------------
    fun startDownlink() {
        Log.d(TAG, "startDownlink() called")
        downlinkStartTime = System.currentTimeMillis()
        downlinkTotalBytes.set(0)
        startUpdateLoop()
    }

    fun addDownlinkBytes(bytes: Int) {
        Log.d(TAG, "addDownlinkBytes() called")
        downlinkTotalBytes.addAndGet(bytes.toLong())    }

    //--------------------------------------------------
    // 上行测试（接收数据）
    //--------------------------------------------------
    fun startUplink() {
        Log.d(TAG, "startUplink() called")
        uplinkStartTime = System.currentTimeMillis()
        uplinkTotalBytes.set(0)
        startUpdateLoop()
    }

    fun addUplinkBytes(bytes: Int) {
        Log.d(TAG, "addUplinkBytes() called")
        uplinkTotalBytes.addAndGet(bytes.toLong())
    }

    //--------------------------------------------------
    // 停止测试
    //--------------------------------------------------
    fun stop() {
        Log.d(TAG, "stop() called")
        updateJob?.cancel()
        updateJob = null
        downlinkMbps = 0f
        uplinkMbps   = 0f
    }

    //--------------------------------------------------
    // 实时更新带宽数据
    //--------------------------------------------------
    fun setUpdateCallback(callback: (Float, Float) -> Unit) {
        Log.d(TAG, "setUpdateCallback() called")
        bandwidthCallback = callback
    }

    private fun startUpdateLoop() {
        updateJob?.cancel()
        updateJob = CoroutineScope(Dispatchers.Default).launch {
            while (isActive) {
                delay(1000)
                calculateBandwidth()
                bandwidthCallback?.invoke(uplinkMbps, downlinkMbps)
            }
        }
    }

    private fun calculateBandwidth() {
        // 下行
        if (downlinkStartTime > 0) {
            val dur = (System.currentTimeMillis() - downlinkStartTime) / 1000f
            downlinkMbps = downlinkTotalBytes.get() * 8 / (dur * 1_000_000f)
        }
        // 上行
        if (uplinkStartTime > 0) {
            val dur = (System.currentTimeMillis() - uplinkStartTime) / 1000f
            uplinkMbps = uplinkTotalBytes.get() * 8 / (dur * 1_000_000f)
        }
    }
}