package com.android.ware.peripheral

import android.app.AppGlobals
import android.app.PackageDeleteObserver
import android.content.ComponentName
import android.content.Intent
import android.content.pm.IPackageDeleteObserver
import android.content.pm.IPackageManager
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.content.pm.VersionedPackage
import android.os.RemoteException
import android.text.TextUtils
import android.util.Log

object LifeKeeper {
    val TAG = "wallwall peripheral PackageKeeper"
    const val PLATFORM_PACKAGE_NAME = "android"

    fun bringUpPackageCompletelyAsUser(packageName: String, userId: Int) {
        setPackageVisibleSettingAsUser(packageName, true, userId)
        setPackageEnabledSettingAsUser(packageName, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, userId)

        val components: Set<ComponentName>? = getAllComponentsWithPackageAsUser(packageName, userId)
        components?.forEach { cn ->
            setComponentEnabledSettingAsUser(cn, PackageManager.COMPONENT_ENABLED_STATE_ENABLED, userId)
        }
    }

    fun setPackageVisibleSettingAsUser(packageName: String, visible: Boolean, userId: Int) {
        if (!TextUtils.isEmpty(packageName)) {
            Log.d(TAG, "setPackageVisableSettingAsUser() packageName: $packageName, visible: $visible, userId: $userId")
            var result = false
            val pm: IPackageManager? = AppGlobals.getPackageManager()
            pm?.let {
                try {
                    if (it.getBlockUninstallForUser(packageName, userId) != visible) {
                        result = it.setBlockUninstallForUser(packageName, visible, userId)
                        Log.d(TAG, "Set block uninstall, result: $result")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error to block uninstall package: $packageName")
                    e.printStackTrace()
                }

                try {
                    if (visible && it.getApplicationHiddenSettingAsUser(packageName, userId)) {
                        result = it.setApplicationHiddenSettingAsUser(packageName, false, userId)
                        Log.d(TAG, "Un-hide settings result: $result")
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error to unhiden package: $packageName")
                    e.printStackTrace()
                }

                try {
                    if (it.isPackageAvailable(packageName, userId) != visible) {
                        if (visible) {
                            val status = it.installExistingPackageAsUser(packageName, userId, 0, PackageManager.INSTALL_REASON_USER, null)
                            Log.d(TAG, "Install package $packageName, result: $status")
                        } else {
                            it.deletePackageVersioned(
                                VersionedPackage(packageName, PackageManager.VERSION_CODE_HIGHEST),
                                LegacyPackageDeleteObserver(null).getBinder(),
                                userId, PackageManager.DELETE_SYSTEM_APP
                            )
                            Log.d(TAG, "uninstall package, package: $packageName")
                        }
                    }
                } catch (e: Exception) {
                    Log.d(TAG, "Error to install package: $packageName")
                    e.printStackTrace()
                }
            }
        }
    }

    fun setPackageSuspendSettingAsUser(packageName: String, suspended: Boolean, userId: Int) {
        if (!TextUtils.isEmpty(packageName)) {
            try {
                val pm: IPackageManager? = AppGlobals.getPackageManager()
                Log.d(TAG, "setPackageSuspendSettingAsUser() packageName: $packageName")
                if (pm != null && pm.isPackageSuspendedForUser(packageName, userId) != suspended) {
                    /* In device owner or managed profile mode,
                     * the callingPackage must be PLATFORM_PACKAGE_NAME("android")
                     * See {PackageManagerService#setPackagesSuspendedAsUser()}
                     */
                    Log.d(TAG, "setPackageSuspendSettingAsUser() packageName: $packageName, suspended: $suspended, userId: $userId")
                    val result = pm.setPackagesSuspendedAsUser(arrayOf(packageName),
                        suspended, null, null, null, 0, PLATFORM_PACKAGE_NAME,
                        userId, userId)
                    result.forEach { unactionedPackage ->
                        Log.d(TAG, "unactioned package: $unactionedPackage")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error to suspended system app: $packageName")
                e.printStackTrace()
            }
        }
    }

    fun setPackageEnabledSettingAsUser(packageName: String, newState: Int, userId: Int) {
        if (!TextUtils.isEmpty(packageName)) {
            try {
                val pm: IPackageManager? = AppGlobals.getPackageManager()
                if (pm != null && pm.getApplicationEnabledSetting(packageName, userId) != newState) {
                    Log.d(TAG, "setPackageEnabledSettingAsUser() packageName: $packageName, newState: $newState, userId: $userId")
                    pm.setApplicationEnabledSetting(packageName, newState,
                        PackageManager.DONT_KILL_APP, userId, null)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error to set \"$packageName\" to newState: $newState, e: $e")
            }
        }
    }

    fun setPackageEnabledSettingWithIntentAsUser(packageName: String, intent: Intent?, newState: Int, userId: Int) {
        if (!TextUtils.isEmpty(packageName) && intent != null) {
            val changedClasses = intent.getStringArrayExtra(Intent.EXTRA_CHANGED_COMPONENT_NAME_LIST)
            if (changedClasses != null) {
                for (changedClass in changedClasses.reversed()) {
                    if (changedClass == null) {
                        continue
                    }
                    if (changedClass == packageName) {
                        setPackageEnabledSettingAsUser(packageName, newState, userId)
                    } else {
                        setComponentEnabledSettingAsUser(
                            ComponentName(packageName, changedClass),
                            newState, userId
                        )
                    }
                }
            }
        }
    }

    private fun setComponentEnabledSettingAsUser(component: ComponentName?, newState: Int, userId: Int) {
        if (component != null) {
            val pm: IPackageManager? = AppGlobals.getPackageManager()
            if (pm != null) {
                try {
                    if (pm.getComponentEnabledSetting(component, userId) != newState) {
                        Log.d(TAG, "setComponentEnabledSettingAsUser() component: $component, newState: $newState, userId: $userId")
                        pm.setComponentEnabledSetting(component, newState,
                            PackageManager.DONT_KILL_APP, userId, null)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Error to set \"$component\" to newState: $newState")
                    e.printStackTrace()
                }
            }
        }
    }

    private fun getAllComponentsWithPackageAsUser(packageName: String, userId: Int): HashSet<ComponentName> {
        val components = HashSet<ComponentName>()
        if (!TextUtils.isEmpty(packageName)) {
            var pkg: PackageInfo? = null
            try {
                val pm: IPackageManager? = AppGlobals.getPackageManager()
                if (pm != null) {
                    val flags = PackageManager.GET_ACTIVITIES.toLong() or
                            PackageManager.GET_SERVICES.toLong() or
                            PackageManager.GET_RECEIVERS.toLong() or
                            PackageManager.GET_PROVIDERS.toLong() or
                            PackageManager.MATCH_DISABLED_COMPONENTS.toLong()
                    pkg = pm.getPackageInfo(packageName, flags, userId)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error to get package info for \"$packageName\".")
                e.printStackTrace()
            }

            if (pkg != null) {
                pkg.activities?.forEach { activity ->
                    components.add(activity.componentName)
                }
                pkg.services?.forEach { service ->
                    components.add(service.componentName)
                }
                pkg.receivers?.forEach { receiver ->
                    components.add(receiver.componentName)
                }
                pkg.providers?.forEach { provider ->
                    components.add(provider.componentName)
                }
            }
        }
        return components
    }

    private class LegacyPackageDeleteObserver(private val mLegacy: IPackageDeleteObserver?) : PackageDeleteObserver() {

        override fun onPackageDeleted(basePackageName: String, returnCode: Int, msg: String) {
            mLegacy?.let {
                try {
                    it.packageDeleted(basePackageName, returnCode)
                } catch (ignored: RemoteException) {
                    // Ignore the exception
                }
            }
        }
    }
}