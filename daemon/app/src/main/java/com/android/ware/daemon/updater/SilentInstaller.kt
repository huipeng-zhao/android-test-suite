package com.android.ware.daemon.updater

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.IntentSender
import android.content.pm.PackageInstaller
import android.content.pm.PackageInstaller.SessionParams
import android.content.pm.PackageManager
import android.os.ParcelFileDescriptor
import android.util.Log
import java.io.FileInputStream
import java.io.IOException
import java.io.OutputStream

class SilentInstaller(context: Context) : BroadcastReceiver() {
    companion object {
        private const val TAG = "SilentInstaller"
        private const val INVALID_SESSION_ID = -1
        private const val REQUEST_ACCEPT_OK = 0
        private const val REQUEST_REFUSE_FAILURE = -1
        private const val ACTION_INSTALL_COMMIT =
            "com.android.ware.daemon.INTENT_PACKAGE_INSTALL_COMMIT"
        private const val PUSH_BYTES_ONCE = 0x10000
    }

    private class SessionInfo(
        val session: PackageInstaller.Session,
        val outStream: OutputStream
    )

    private val mContext: Context = context
    private val mInstallationSessions = HashMap<Int, SessionInfo>()

    override fun onReceive(context: Context, intent: Intent) {
        notifyPackageInstalled(intent)
    }

    fun silentInstallPackage(fileDescriptor: ParcelFileDescriptor): Int {
        val sessionId = openSession(fileDescriptor)
        return commit(sessionId)
    }

    private fun openSession(fileDescriptor: ParcelFileDescriptor): Int {
        var sessionId: Int
        var outputStream: OutputStream? = null
        var inputStream: FileInputStream? = null
        val params = SessionParams(SessionParams.MODE_FULL_INSTALL)

        sessionId = INVALID_SESSION_ID
        params.installFlags = params.installFlags or PackageManager.INSTALL_DISABLE_VERIFICATION
        val packageInstaller = mContext.packageManager.packageInstaller
        try {
            sessionId = packageInstaller.createSession(params)
            val session = packageInstaller.openSession(sessionId)
            outputStream = session.openWrite(
                "SilentInstaller$sessionId", 0, fileDescriptor.statSize
            )
            inputStream = FileInputStream(fileDescriptor.fileDescriptor)
            val data = ByteArray(PUSH_BYTES_ONCE)
            var len: Int
            while ((inputStream.read(data).also { len = it }) >= 0) {
                outputStream.write(data, 0, len)
            }
            synchronized(mInstallationSessions) {
                mInstallationSessions.put(sessionId, SessionInfo(session, outputStream))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start installation $e")
            try {
                outputStream?.close()
            } catch (ioe: IOException) {
                Log.e(TAG, "Failed to close outputStream $ioe")
            }
            clearSessionInfoLocked(sessionId)
            sessionId = INVALID_SESSION_ID
        } finally {
            try {
                inputStream?.close()
                fileDescriptor.close()
            } catch (ioe: IOException) {
                Log.e(TAG, "Failed to close inputStream or fileDescriptor $ioe")
            }
        }
        return sessionId
    }

    private fun commit(sessionId: Int): Int {
        val session: SessionInfo? = getSessionInfoLocked(sessionId)
        if (session != null) {
            try {
                session.session.fsync(session.outStream)
                session.outStream.close()
                session.session.commit(getCommitCallback(sessionId))
                return REQUEST_ACCEPT_OK
            } catch (e: java.lang.Exception) {
                clearSessionInfoLocked(sessionId)
            } finally {
                try {
                    session.outStream.close()
                    session.session.close()
                } catch (e: java.lang.Exception) {
                    Log.e(TAG, "Failed to close OutStream or session $e")
                }
            }
        }
        Log.e(TAG, "Failed to install package, due to internal error.")
        return REQUEST_REFUSE_FAILURE
    }

    private fun getCommitCallback(sessionId: Int): IntentSender {
        val intentFilter = IntentFilter(ACTION_INSTALL_COMMIT)
        mContext.registerReceiver(this, intentFilter)

        val broadcastIntent = Intent(ACTION_INSTALL_COMMIT)
        broadcastIntent.putExtra(PackageInstaller.EXTRA_SESSION_ID, sessionId)
        val pendingIntent = PendingIntent.getBroadcast(mContext, sessionId,
                broadcastIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        return pendingIntent.intentSender
    }

    private fun notifyPackageInstalled(intent: Intent) {
        if (ACTION_INSTALL_COMMIT == intent.action) {
            val sessionId = intent.getIntExtra(PackageInstaller.EXTRA_SESSION_ID,
                    INVALID_SESSION_ID)
            val returnCode = intent.getIntExtra(PackageInstaller.EXTRA_STATUS,
                    PackageInstaller.STATUS_FAILURE)
            val returnMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            val packageName = intent.getStringExtra(PackageInstaller.EXTRA_PACKAGE_NAME)
            Log.d(TAG, "Got installation callback for package = " + packageName
                    + ", result code = " + returnCode + ", result message = " + returnMessage)
            val session: SessionInfo? = getSessionInfoLocked(sessionId)
            if (session != null) {
                clearSessionInfoLocked(sessionId)
                if (returnCode == PackageInstaller.STATUS_SUCCESS) {
                    Log.i(TAG, "Install success")
                } else {
                    Log.e(TAG, "Install failed $returnCode")
                }
            }
        }
    }

    private fun getSessionInfoLocked(sessionId: Int): SessionInfo? {
        synchronized(mInstallationSessions) {
            if (mInstallationSessions.containsKey(sessionId)) {
                return mInstallationSessions[sessionId]
            }
        }
        return null
    }

    private fun clearSessionInfoLocked(sessionId: Int) {
        synchronized(mInstallationSessions) {
            mInstallationSessions.remove(sessionId)
            if (mInstallationSessions.size == 0) {
                try {
                    mContext.unregisterReceiver(this)
                } catch (e: IllegalArgumentException) {
                    // Happens when session created, but receiver was not registered yet.
                }
            }
        }
    }
}
