package com.calibur.nfchcetest

import HceServiceManager
import android.content.ComponentName
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class BtPairEmulatorActivity : TestActivityBase() {
    private lateinit var btPairView: MyTextView
    private lateinit var mHtmlText: HtmlText

    private var mServiceInitialized = false
    private lateinit var mHandler: Handler
    private var mEnabledServices = ArrayList<ComponentName>()

    companion object {
        const val TAG = "BtPairEmulatorActivity"
    }

//    private val requestBluetoothPermissionLauncher =
//        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
//            if (isGranted) {
//                setupServices(HceBtPairService.COMPONENT)
//            } else {
//                // 权限被拒绝，提示用户需要权限
//                Log.e(TAG, "No Permission")
//            }
//        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_bt_pair_emulator)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.emulator_bt_pair)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initView()
    }

    override fun onDestroy() {
        super.onDestroy()
        HceServiceManager.disableAllServices(this, null)
    }

    private fun initView() {
        supportActionBar?.apply {
            title = getString(R.string.nfc_hce_bt_pair_emulator)
        }
        btPairView = findViewById(R.id.bt_pair_content_tv)
        mHtmlText = HtmlText(this, btPairView, R.id.scroll)
        mHandler = Handler(Looper.getMainLooper())
        setupServices(HceBtPairService.COMPONENT)

    }

    private fun setupServices(vararg components: ComponentName) {
        Log.d(NdefEmulatorActivity.TAG, "setupServices")
        if (mServiceInitialized) {
            emulateServiceSetupFinished(mHceServiceSetupListener, true)
            return
        }
        showProgressDialog(
            getString(R.string.nfc_hce_please_wait),
            getString(R.string.nfc_hce_setting_up)
        )
        HceServiceManager.setupServices(this, mHceServiceSetupListener, *components)
    }

    private fun emulateServiceSetupFinished(
        listener: HceServiceManager.HceServiceSetupListener,
        result: Boolean
    ) {
        Log.d(NdefEmulatorActivity.TAG, "emulateServiceSetupFinished")
        mHandler.postDelayed({ listener.onServiceSetupFinished(result) }, 200)
    }

    private val mHceServiceSetupListener = HceServiceManager.HceServiceSetupListener { result ->
        mEnabledServices = HceServiceManager.getEnabledServices() as ArrayList<ComponentName>
        dismissDialog()
        mServiceInitialized = true
        outputEnabledServices()
        outputExpectedResult()
    }

    private fun outputEnabledServices() {
        mHtmlText.addSubject1("Enabled Services:")
        Log.d(NdefEmulatorActivity.TAG, "mEnabledServices num=${mEnabledServices.size}")
        val sb = StringBuilder()
        sb.append(mHtmlText.getText("\t\tHost", HtmlText.TRANS_GREEN1))
        sb.append(mHtmlText.getText(", other", HtmlText.TRANS_GREEN1))
        sb.append(mHtmlText.getText(", ${Util.BT_PAIR_AID}", HtmlText.TRANS_GREEN1))
        sb.append(mHtmlText.getText(", requiresUnlock", HtmlText.TRANS_GREEN1))
        mHtmlText.addTextLine(sb.toString().trim())
        mHtmlText.addTextLine("")
    }

    private fun outputExpectedResult() {
//        mHtmlText.addSubject1("Expected Result:")
//        mHtmlText.addTextLine("URL: baidu.com")
//        mHtmlText.addTextLine("Text: 您好")
//        mHtmlText.addTextLine("Custom Type: com.example.custom, Data: Custom Data Example")
    }
}