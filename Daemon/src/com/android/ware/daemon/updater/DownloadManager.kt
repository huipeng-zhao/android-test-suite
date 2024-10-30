package com.android.ware.daemon.updater

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class DownloadManager(context: Context) {
    private val TAG = "wallwall DownloadManager"
    val urlOtaStr = "http://172.18.3.72:8000/ota.zip"
    val urlAPKStr = "http://172.18.3.72:8000/PeripheralService.apk"
    private val mContext: Context = context
    private val handler = Handler(Looper.getMainLooper())

    fun checkFileExists(urlStr: String): Boolean {
        val client = OkHttpClient()

        val request = Request.Builder()
            .url(urlStr)
            .head()
            .build()

        return try {
            val response = client.newCall(request).execute()
            response.isSuccessful
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun downloadFileAsync(urlStr: String, destinationPath: String) {
        val client = OkHttpClient()

        Log.d(TAG, "File downloaded urlStr $urlStr")
        Log.d(TAG, "File downloaded destinationPath $destinationPath")

        Thread {
            try {
                val request = Request.Builder()
                    .url(urlStr)
                    .build()
                val response: Response = client.newCall(request).execute()
                if (!response.isSuccessful) throw IOException("Unexpected code $response")

                val file = File(destinationPath)
                FileOutputStream(file).use { outputStream ->
                    outputStream.write(response.body()?.bytes())
                }

                handler.post {
                    Log.d(TAG, "File downloaded successfully to $destinationPath")

                    var intent: Intent = Intent("")
                    if (urlOtaStr === urlStr) {
                        Log.d(TAG, "File downloaded OTA*********")
                        intent = Intent("com.android.ware.daemon.OTADownload")
                    }
                    if (urlAPKStr === urlStr) {
                        Log.d(TAG, "File downloaded APK*********")
                        intent = Intent("com.android.ware.daemon.APKDownload")
                    }
                    mContext.sendBroadcast(intent)

                    /*Log.d(TAG, "start to parse ota ")
                    try {
                        val mSilentUpdater: SilentUpdater = SilentUpdater(mContext)
                        mSilentUpdater.trigger(destinationPath);
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to upload due to unknown reason, e: $e")
                        throw IllegalArgumentException(e.message)
                    }*/
                    /*Log.d(TAG, "start to install ")
                    var fileDescriptor: ParcelFileDescriptor? = null
                    try {
                        val file = File(destinationPath)
                        fileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)

                        val silentInstaller = SilentInstaller(mContext)
                        silentInstaller.silentInstallPackage(fileDescriptor)
                    } catch (e: IOException) {
                        Log.e(TAG, "Failed to install due to unknown reason, e: $e")
                        throw IllegalArgumentException(e.message)
                    }*/
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Log.d(TAG, "Exception")
                handler.post {
                    Log.d(TAG, "Failed to install package, e: $e")
                }
            }
        }.start()
    }
}