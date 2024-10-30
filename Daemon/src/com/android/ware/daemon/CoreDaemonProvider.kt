package com.android.ware.daemon

import android.content.BroadcastReceiver
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.ConnectivityManager

import android.database.Cursor
import android.net.Uri
import android.os.PatternMatcher
import android.util.Log

import com.android.ware.daemon.lifekeeper.LifeKeeper
import com.android.ware.daemon.lifekeeper.PackageKeeper
import com.android.ware.daemon.updater.NetworkChangeReceiver
import com.android.ware.daemon.updater.UserController

class CoreDaemonProvider : ContentProvider() {
    private lateinit var mLifeKeeper: LifeKeeper
    companion object {
        private const val TAG = "wallwall CoreDaemon"
        const val PACKAGE_PERIPHERAL_SERVICE = "com.android.ware.peripheral"
        const val PACKAGE_DAEMON = "com.android.ware.daemon"
        const val PACKAGE_ACTION_SCHEME = "package"
        const val PLATFORM_PACKAGE_NAME = "android"
    }
    private lateinit var networkChangeReceiver: NetworkChangeReceiver

    override fun onCreate(): Boolean {
        mLifeKeeper = LifeKeeper(context!!)
        mLifeKeeper.trigger()
        Log.i(TAG, "Daemon bring-up！！")
        context?.let {
            networkChangeReceiver = NetworkChangeReceiver(it)
            val filter = IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION)
            it.registerReceiver(networkChangeReceiver, filter)
        }

        val userId = UserController.getCurrentUserId(context)
        Log.i(TAG, "onCreate() userId: $userId")
        PackageKeeper.setPackageSuspendSettingAsUser(PACKAGE_PERIPHERAL_SERVICE, false, userId)

        if (UserController.isPrimaryUserStarted()) {
            val userIds = UserController.getUsers(context, UserController.FLAG_PRIMARY_USER or UserController.FLAG_MANAGED_PROFILE)
            userIds.forEach { id ->
                PackageKeeper.bringUpPackageCompletelyAsUser(PACKAGE_DAEMON, id)
                PackageKeeper.setPackageSuspendSettingAsUser(PACKAGE_DAEMON, false, id)
            }
        }

        context?.let {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme(PACKAGE_ACTION_SCHEME)
                addDataSchemeSpecificPart(PACKAGE_DAEMON, PatternMatcher.PATTERN_LITERAL)
            }
            it.registerReceiver(LifeKeeperBroadcastReceiver(), filter, null, null)

            val filter2 = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGES_SUSPENDED)
                addAction(Intent.ACTION_USER_STARTED)
            }
            it.registerReceiver(LifeKeeperBroadcastReceiver(), filter2, null, null)
        }

        context?.let {
            val filter = IntentFilter().apply {
                addAction("com.android.ware.daemon.APKDownload")
                addAction("com.android.ware.daemon.OTADownload")
            }
            it.registerReceiver(DownloadBroadcastReceiver(), filter, null, null)
        }

        return true
    }

    override fun query(
        uri: Uri,
        projection: Array<out String>?,
        selection: String?,
        selectionArgs: Array<out String>?,
        sortOrder: String?
    ): Cursor? {
        throw UnsupportedOperationException("Invalid operation query().")
    }

    override fun getType(uri: Uri): String? {
        throw UnsupportedOperationException("Invalid operation getType().")
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? {
        throw UnsupportedOperationException("Invalid operation insert().")
    }

    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int {
        throw UnsupportedOperationException("Invalid operation delete().")
    }

    override fun update(
        uri: Uri,
        values: ContentValues?,
        selection: String?,
        selectionArgs: Array<out String>?
    ): Int {
        throw UnsupportedOperationException("Invalid operation update().")
    }

    private class LifeKeeperBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context != null && intent != null && intent.action != null) {
                /*val userId = UserController.getCarriedUserIdWithIntent(intent)
                Log.d(TAG, "onReceive(), userId: $userId")

                if (!UserController.isPrimaryUser(userId) && !UserController.isManagedProfileUser(context, userId)) {
                    Log.d(TAG, "Carried user isn't primary or managed profile user.")
                    return
                }*/
                val userId = 0;

                val data = intent.data
                val packageName = data?.encodedSchemeSpecificPart?.trim()
                if (!packageName.isNullOrEmpty()) {
                    Log.d(TAG, "onReceive(), packageName: $packageName")
                } else {
                    Log.e(TAG, "Package name is null or empty.")
                    return
                }
                Log.d(TAG, "onReceive(), action: ${intent.action}")

                when (intent.action) {
                    Intent.ACTION_PACKAGE_REMOVED -> {
                        if (!intent.getBooleanExtra(Intent.EXTRA_REPLACING, false)) {
                            PackageKeeper.setPackageVisibleSettingAsUser(packageName, true, userId)
                            Log.d(TAG, "Re-visible package \"$packageName\".")
                        }
                    }
                    Intent.ACTION_PACKAGE_CHANGED -> {
                        PackageKeeper.setPackageEnabledSettingWithIntentAsUser(
                            packageName, intent, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, userId
                        )
                        Log.w(TAG, "Re-enable package \"$packageName\".")
                    }
                    Intent.ACTION_PACKAGES_SUSPENDED -> {
                        val packages = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_PACKAGE_LIST)
                        packages?.forEach { p ->
                            if (PACKAGE_PERIPHERAL_SERVICE == p || PACKAGE_DAEMON == p) {
                                Log.w(TAG, "Un-suspend package \"$p\".")
                                PackageKeeper.setPackageSuspendSettingAsUser(p, false, userId)
                            }
                        }
                    }
                    Intent.ACTION_USER_STARTED -> {
                        Log.w(TAG, "Bring up action package when user-started.")
                        PackageKeeper.bringUpPackageCompletelyAsUser(PACKAGE_DAEMON, userId)
                        PackageKeeper.setPackageSuspendSettingAsUser(PACKAGE_DAEMON, false, userId)
                    }
                    else -> {
                        // Do nothing for other actions
                    }
                }
            }
        }
    }

    private class DownloadBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context != null && intent != null && intent.action != null) {
                when (intent.action) {
                    "com.android.ware.daemon.APKDownload" -> {
                        Log.w(TAG, "APK Download successful ********")
                    }
                    "com.android.ware.daemon.OTADownload" -> {
                        Log.w(TAG, "OTA Download successful *******")
                    }
                    else -> {
                        // Do nothing for other actions
                    }
                }
            }
        }
    }
}