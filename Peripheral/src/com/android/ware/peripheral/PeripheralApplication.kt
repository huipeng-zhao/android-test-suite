package com.android.ware.peripheral

import android.app.Application
import android.content.pm.PackageManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.PatternMatcher
import android.text.TextUtils
import android.util.Log

class PeripheralApplication : Application() {
    companion object {
        private const val TAG = "wallwall PeripheralApplication"
        const val PACKAGE_PERIPHERAL_SERVICE = "com.android.ware.peripheral"
        const val PACKAGE_ACTION_SCHEME = "package"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "Application created")
        LifeKeeper.bringUpPackageCompletelyAsUser(PACKAGE_PERIPHERAL_SERVICE, 0)
        //acquireEnterpriseServiceProvider()

        this?.let {
            val filter = IntentFilter().apply {
                addAction(Intent.ACTION_PACKAGE_ADDED)
                addAction(Intent.ACTION_PACKAGE_REMOVED)
                addAction(Intent.ACTION_PACKAGE_CHANGED)
                addDataScheme(PACKAGE_ACTION_SCHEME)
                addDataSchemeSpecificPart(
                    PACKAGE_PERIPHERAL_SERVICE,
                    PatternMatcher.PATTERN_LITERAL
                )
            }

            it.registerReceiver(LifeKeeperBroadcastReceiver(), filter, null, null)
        }
    }

    /*private fun acquireEnterpriseServiceProvider(): Boolean {
        var provider: IContentProvider? = null
        var result = false
        val resolver: ContentResolver = this.getContentResolver()
        try {
            /* Acquire provider can make AM to install the given provider
             * (calls provider#onCreate()) first, if provider isn't installed.
             */
            provider = resolver.acquireProvider(Constants.CATEGORY_DEVICE_POLICIES)
            result = (provider != null)
        } catch (e: Exception) {
            e.printStackTrace()
            Log.e(TAG, "Acquire core package provider failed!")
        } finally {
            if (provider != null) resolver.releaseProvider(provider)
        }
        Log.i(TAG, "Acquire core package provider result: $result")
        return result
    }*/

    private class LifeKeeperBroadcastReceiver : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (context != null && intent != null && intent.getAction() != null) {
                val data: Uri? = intent.getData()
                val packageName: String? =
                    if ((data != null)) data.getEncodedSchemeSpecificPart() else null
                if (!TextUtils.isEmpty(packageName)) packageName!!.trim { it <= ' ' }
                Log.d(TAG, "onReceive(), packageName: $packageName")
                if (!PACKAGE_PERIPHERAL_SERVICE.equals(packageName)) {
                    Log.d(
                        TAG, ("Carried package isn't " +
                                PACKAGE_PERIPHERAL_SERVICE).toString() + "."
                    )
                    return
                }
                Log.d(TAG, "onReceive(), action: " + intent.getAction())
                when (intent.getAction()) {
                    Intent.ACTION_PACKAGE_ADDED -> {
                        //acquireEnterpriseServiceProvider()
                        Log.w(TAG, "Added \"$packageName\".")
                    }

                    Intent.ACTION_PACKAGE_REMOVED -> if (!intent.getBooleanExtra(
                            Intent.EXTRA_REPLACING,
                            false
                        )
                    ) {
                        LifeKeeper.setPackageVisibleSettingAsUser(packageName, true, 0)
                        Log.d(TAG, "Re-visible \"$packageName\".")
                    }

                    Intent.ACTION_PACKAGE_CHANGED -> {
                        LifeKeeper.setPackageEnabledSettingWithIntentAsUser(
                            packageName, intent,
                            PackageManager.COMPONENT_ENABLED_STATE_ENABLED, 0
                        )
                        Log.d(TAG, "Re-enable \"$packageName\".")
                    }

                    else -> {}
                }
            }
        }
    }
}