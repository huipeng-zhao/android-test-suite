package com.android.ware.peripheral

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log

class PeripheralService : Service() {
    private val TAG = "wallwall PeripheralService"

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Service created")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "Service started")
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "Service destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}