package com.android.ware.daemon.updater

import android.content.Context
import android.os.PowerManager
import android.os.UpdateEngine
import android.os.UpdateEngine.ErrorCodeConstants.SUCCESS
import android.os.UpdateEngine.ErrorCodeConstants.UPDATED_BUT_NOT_ACTIVE
import android.os.UpdateEngineCallback
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

class SilentUpdater(context: Context) : UpdateEngineCallback() {
    companion object {
        private const val TAG = "SilentUpdater"
        private const val CLEANUP_STATUS_DEFAULT = -100
    }
    private val mUpdateEngine: UpdateEngine = UpdateEngine()
    private val mUpdateParser: UpdateParser = UpdateParser()
    private var mCleanUpStatus: Int = CLEANUP_STATUS_DEFAULT;
    private var mContext: Context = context;

    fun trigger(url: String) {
        mUpdateEngine.bind(this)
        val exec = Executors.newSingleThreadExecutor()
        val cleanup = Runnable {
            mCleanUpStatus = mUpdateEngine.cleanupAppliedPayload()
            Log.i(TAG, "cleanupAppliedPayload end :$mCleanUpStatus")
            install(url)
        }
        Log.i(TAG, "cleanupAppliedPayload start")
        exec.execute(cleanup)
    }

    private fun install(url: String) {
        val file = File(url)
        if (!file.exists()) return
        try {
            mUpdateParser.parse(file)
        } catch (ioe: IOException) {
            Log.w(TAG, "install:" + ioe.toString())
            return
        }
        mUpdateEngine.resetStatus()
        mUpdateEngine.applyPayload(mUpdateParser.mUrl, mUpdateParser.mOffset,
                mUpdateParser.mSize, mUpdateParser.mProps)
    }

    override fun onStatusUpdate(status: Int, percent: Float) {
        Log.d(TAG, "Status: " + status + " Percentage: " + percent);
    }

    override fun onPayloadApplicationComplete(errorCode: Int) {
        Log.d(TAG, "PayloadApplication Complete. ECD: " + errorCode);
        if (errorCode == UPDATED_BUT_NOT_ACTIVE || errorCode == SUCCESS) {
            val powerManager = mContext.getSystemService(PowerManager::class.java)
            powerManager.reboot("SilentUpdater")
        }
    }
}