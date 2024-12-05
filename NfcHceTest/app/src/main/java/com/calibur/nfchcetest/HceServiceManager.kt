import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.nfc.NfcAdapter
import android.nfc.cardemulation.CardEmulation
import android.os.AsyncTask
import android.util.Log
import com.calibur.nfchcetest.HceNdefService
import com.calibur.nfchcetest.TransportService1
import com.calibur.nfchcetest.Util

class HceServiceManager {

    companion object {
        private val sEnabledServices = ArrayList<ComponentName>()

        private val TAG = "HceServiceManager"

        private val sServices = arrayListOf(
            TransportService1.COMPONENT,
            HceNdefService.COMPONENT
        )

        fun setupServices(
            context: Context,
            callback: HceServiceSetupListener,
            vararg components: ComponentName
        ) {
            Log.d(TAG, "setupServices: ")
            SetupServicesTask(context, callback).execute(*components)
        }

        fun disableAllServices(context: Context, callback: HceServiceSetupListener?) {
            Log.d(TAG, "disableAllServices:")
            SetupServicesTask(context, callback).execute()
        }

        private class SetupServicesTask(
            context: Context,
            private val callback: HceServiceSetupListener?
        ) : AsyncTask<ComponentName, Void, Boolean>() {

            private val mCardEmulation =
                CardEmulation.getInstance(NfcAdapter.getDefaultAdapter(context))
            private val mPm = context.packageManager

            override fun doInBackground(vararg params: ComponentName): Boolean {
                val ret: Boolean = if (params.isNotEmpty()) {
                    enable(*params)
                } else {
                    disableAll()
                }

                if (ret) {
                    try {
                        val bogusComponent =
                            ComponentName(Util.PACKAGE, "${Util.PACKAGE}.BogusService")
                        mCardEmulation.isDefaultServiceForCategory(
                            bogusComponent,
                            CardEmulation.CATEGORY_PAYMENT
                        )
                    } catch (e: Exception) {
                        Log.e(TAG, "SetupServicesTask: $e")
                    }
                }
                return ret
            }

            private fun enableComponent(component: ComponentName) {
                if (mPm.getComponentEnabledSetting(component) != PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    Log.d(TAG, "Enabling component ${component.className}")
                    mPm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }

            private fun disableComponent(component: ComponentName) {
                if (mPm.getComponentEnabledSetting(component) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED) {
                    Log.d(TAG, "Disabling component ${component.className}")
                    mPm.setComponentEnabledSetting(
                        component,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }

            private fun enable(vararg params: ComponentName): Boolean {
                return try {
                    val enableComponents = params.toList()

                    for (param in enableComponents) {
                        if (!sServices.contains(param)) {
                            Log.e(TAG, "Not found in sServices: $param")
                            return false
                        }
                    }

                    for (component in sServices) {
                        if (enableComponents.contains(component)) {
                            if (!sEnabledServices.contains(component)) {
                                enableComponent(component)
                                sEnabledServices.add(component)
                            }
                        } else {
                            disableComponent(component)
                            sEnabledServices.remove(component)
                        }
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())
                    false
                }
            }

            private fun disableAll(): Boolean {
                Log.d(TAG, "disableAll")
                return try {
                    for (component in sServices) {
                        disableComponent(component)
                        sEnabledServices.remove(component)
                    }
                    true
                } catch (e: Exception) {
                    Log.e(TAG, e.toString())
                    false
                }
            }

            override fun onPostExecute(result: Boolean) {
                super.onPostExecute(result)
                callback?.onServiceSetupFinished(result)
            }
        }

        fun getEnabledServices(): List<ComponentName> {
            return ArrayList(sEnabledServices) // Return a copy for safety
        }
    }

    fun interface HceServiceSetupListener {
        fun onServiceSetupFinished(result: Boolean)
    }
}
