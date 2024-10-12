package com.android.ware.daemon.lifekeeper

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Message
import android.os.Messenger
import android.os.UserHandle
import android.util.Log

class LifeKeeper(context: Context) : ServiceConnection, BroadcastReceiver() {
    companion object {
        private const val TAG = "LifeKeeper"
        private const val PERIPHERAL_PACKAGE = "com.android.ware.peripheral "
        private const val PERIPHERAL_SERVICE_CLASS: String = ".PeripheralService"
        private val mPeripheralServiceComponent: ComponentName =
            ComponentName(PERIPHERAL_PACKAGE, PERIPHERAL_SERVICE_CLASS)
        private const val ASSOCIATE_REQUEST = 0x01
    }

    private val mContext: Context = context
    private var mMessenger: Messenger? = null
    private var mConnecting: Boolean = false

    fun trigger() {
        val filter = IntentFilter()
        filter.addAction(Intent.ACTION_USER_STARTING)
        mContext.registerReceiver(this, filter, null, null)
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != null) {
            val userId: Int = getCarriedUserIdWithIntent(intent)
            Log.d(TAG, "onReceive(), userId: $userId")
            if (userId == UserHandle.USER_SYSTEM) connect()
        }
    }

    override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
        mMessenger = Messenger(service)
        mConnecting = false
        val message = Message.obtain(null, ASSOCIATE_REQUEST)
        try {
            mMessenger!!.send(message)
            Log.i(TAG, "Connect and associate success!")
        } catch (e: android.os.RemoteException) {
            Log.e(TAG, "Associate  failed!")
            reconnect()
        }
    }

    override fun onServiceDisconnected(name: ComponentName?) {
        Log.e(TAG, "onServiceDisconnected()")
        reconnect()
    }

    override fun onBindingDied(name: ComponentName?) {
        Log.e(TAG, "onBindingDied()")
        reconnect()
    }

    private fun connect() {
        if (mMessenger != null || mConnecting) return
        val intent = Intent().setComponent(mPeripheralServiceComponent)
        val flags = Context.BIND_IMPORTANT or Context.BIND_AUTO_CREATE
        val result = mContext.bindServiceAsUser(intent, this, flags, UserHandle.SYSTEM)
        if (result) mConnecting = true
        else Log.d(TAG, "bindServiceAsUser failed!")
    }

    private fun reconnect() {
        mMessenger = null
        mConnecting = false
        connect()
    }

    private fun getCarriedUserIdWithIntent(intent: Intent?): Int {
        if (intent != null) {
            val userHandle = intent.getParcelableExtra<UserHandle>(Intent.EXTRA_USER)
            if (userHandle != null) {
                val userId = userHandle.identifier
                if (UserHandle.USER_NULL != userId) return userId
            }
            return intent.getIntExtra(Intent.EXTRA_USER_HANDLE, UserHandle.USER_NULL)
        }
        return UserHandle.USER_NULL
    }
}