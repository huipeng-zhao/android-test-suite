package com.android.ware.daemon.updater

import android.app.ActivityManager
import android.app.ActivityManagerInternal
import android.content.Context
import android.content.Intent
import android.os.Binder
import android.os.UserHandle
import android.os.UserManager
import android.util.Log

import com.android.server.LocalServices

object UserController {
    private const val TAG = "wallwall UserController"
    const val FLAG_PRIMARY_USER = 1 shl 0
    const val FLAG_MANAGED_PROFILE = 1 shl 1

    fun isPrimaryUserStarted(): Boolean {
        val ami = LocalServices.getService(ActivityManagerInternal::class.java)
        return if (ami != null) {
            val booting = ami.isBooting()
            val booted = ami.isBooted()
            Log.i(TAG, "Primary user booting: $booting, booted: $booted")
            booting || booted
        } else {
            false
        }
    }


    fun getUsers(context: Context?, flags: Int): List<Int> {
        val userIds = ArrayList<Int>()
        if (context != null) {
            val um = context.getSystemService(Context.USER_SERVICE) as? UserManager
            if (um != null) {
                val users = um.getAliveUsers()
                for (user in users) {
                    if (flags and FLAG_PRIMARY_USER != 0) {
                        if (UserHandle.USER_SYSTEM == user.id) userIds.add(user.id)
                    }
                    if (flags and FLAG_MANAGED_PROFILE != 0) {
                        if (user.isManagedProfile) userIds.add(user.id)
                    }
                }
            }
        }
        return userIds
    }

    fun getCarriedUserIdWithIntent(intent: Intent?): Int {
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

    fun isManagedProfileUser(context: Context): Boolean {
        return isManagedProfileUser(context, getCurrentUserId(context))
    }

    fun isManagedProfileUser(context: Context, userId: Int): Boolean {
        if (context != null) {
            val callingId = Binder.clearCallingIdentity()
            return try {
                val userManager = context.getSystemService(Context.USER_SERVICE) as? UserManager
                userManager?.isManagedProfile(userId) ?: false
            } finally {
                Binder.restoreCallingIdentity(callingId)
            }
        }
        return false
    }

    fun isPrimaryUser(): Boolean {
        return isPrimaryUser(getCurrentUserId())
    }

    fun isPrimaryUser(userId: Int): Boolean {
        return UserHandle.USER_SYSTEM == userId
    }

    fun getCurrentUserId(): Int {
        return getCurrentUserId(null)
    }

    fun getCurrentUserId(context: Context?): Int {
        return context?.userId ?: ActivityManager.getCurrentUser()
    }
}