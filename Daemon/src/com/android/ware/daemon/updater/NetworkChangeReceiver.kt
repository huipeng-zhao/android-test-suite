package com.android.ware.daemon.updater

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import java.io.File
import android.os.Environment
import java.io.IOException

class NetworkChangeReceiver(private val context: Context) : BroadcastReceiver() {
    private val TAG = "wallwall NetworkChangeReceiver"
    private val mDownloadManager = DownloadManager(context)
    val urlOtaStr = "http://172.18.3.72:8000/ota.zip"
    val urlStr = "http://172.18.3.72:8000/PeripheralService.apk"

    override fun onReceive(context: Context, intent: Intent) {
        if (isNetworkAvailable(context)) {
            Log.d(TAG, "Network is available")
            if (mDownloadManager.checkFileExists(urlStr)) {
                Log.d(TAG, "*****APK file exit*****")
                val destinationPath = "/data/user_de/0/com.android.ware.daemon/cache/PeripheralService.apk"
                mDownloadManager.downloadFileAsync(urlStr, destinationPath ?: "")
            } else {
                Log.d(TAG, "#####APK file not exit########")
            }

            if (mDownloadManager.checkFileExists(urlOtaStr)) {
                Log.d(TAG, "*****OTA file exit*****")
                val destinationOtaPath = "/storage/emulated/0/ota.zip"
                mDownloadManager.downloadFileAsync(urlOtaStr, destinationOtaPath ?: "")

            } else {
                Log.d(TAG, "#####OTA file not exit########")
            }
        } else {
            Log.d(TAG, "Network is not available")
        }
    }

    private fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val networkCapabilities = connectivityManager.getNetworkCapabilities(connectivityManager.activeNetwork)
        return networkCapabilities?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
    }
}