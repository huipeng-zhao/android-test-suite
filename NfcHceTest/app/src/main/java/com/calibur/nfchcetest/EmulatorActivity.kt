package com.calibur.nfchcetest

import HceServiceManager
import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class EmulatorActivity : TestActivityBase() {
    private val TAG = "NfcHceTest_EmulatorActivity"
    private val KEY_SERVICE_INITIALIZED = "serviceInitialized"

    private lateinit var mTextView: MyTextView
    private lateinit var mHtmlText: HtmlText

    private var mServiceInitialized = false
    private lateinit var mHandler: Handler
    private var mEnabledServices = ArrayList<ComponentName>()
    private var mIsFirstApduSequence = true

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_common)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scroll)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (savedInstanceState != null) {
            mServiceInitialized = savedInstanceState.getBoolean(KEY_SERVICE_INITIALIZED)
        }

        initView()
        initData()
    }

    private fun initView() {
        supportActionBar?.apply {
            title = getString(R.string.nfc_hce_emulator_tests)
            setBackgroundDrawable(ColorDrawable(getColor(R.color.bg1_common)))
        }
        mTextView = findViewById(R.id.text)
        mHtmlText = HtmlText(this, mTextView, R.id.scroll)
        findViewById<View>(R.id.scroll).setBackgroundColor(
            ContextCompat.getColor(
                this,
                R.color.bg1_common
            )
        )
    }

    private fun initData() {
        mHandler = Handler(Looper.getMainLooper())
        setupServices(TransportService1.COMPONENT)
    }

    override fun onRestoreInstanceState(savedInstanceState: Bundle) {
        super.onRestoreInstanceState(savedInstanceState)
        mServiceInitialized = savedInstanceState.getBoolean(KEY_SERVICE_INITIALIZED, false)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_SERVICE_INITIALIZED, mServiceInitialized)
    }

    override fun onResume() {
        super.onResume()
        val filter = IntentFilter().apply {
            addAction(Util.ACTION_SEQUENCE_COMPLETE)
            addAction(Util.ACTION_SEQ_LOG_SEND)
            addAction(Util.ACTION_SEQ_LOG_RECV)
            addAction(Util.ACTION_DEACTIVATED)
        }
        registerReceiver(mReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
    }

    override fun onPause() {
        super.onPause()
        unregisterReceiver(mReceiver)
    }

    private val mReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            when (intent.action) {
                Util.ACTION_SEQUENCE_COMPLETE -> {
                    val component: ComponentName? = intent.getParcelableExtra(Util.EXTRA_COMPONENT)
                    val duration: Long = intent.getLongExtra(Util.EXTRA_DURATION, 0)
                    component?.let { onApduSequenceComplete(it, duration) }
                }

                Util.ACTION_SEQUENCE_ERROR -> {
                    onApduSequenceError()
                    mHtmlText.addTextLineRed("An error occurred.")
                }

                Util.ACTION_SEQ_LOG_SEND, Util.ACTION_SEQ_LOG_RECV -> {
                    if (mIsFirstApduSequence) {
                        mHtmlText.addSubject1("Actual APDU Sequence:")
                        mIsFirstApduSequence = false
                    }
                    val apdu: ByteArray? = intent.getByteArrayExtra(Util.EXTRA_REQ_LOG)
                    val who: String? = intent.getStringExtra(Util.EXTRA_WHO)
                    when (intent.action) {
                        Util.ACTION_SEQ_LOG_SEND -> {
                            mHtmlText.addText("Send: ${Util.toHexString(apdu)}")
                        }

                        Util.ACTION_SEQ_LOG_RECV -> {
                            mHtmlText.addTextYellow("Recv: ${apdu?.let { getParsedApduHtmlText(it) }}")
                        }
                    }
                    mHtmlText.addTextLine(" - $who", HtmlText.LIGHTGRAY1)
                }

                Util.ACTION_DEACTIVATED -> {
                    val reason: String? = intent.getStringExtra(Util.EXTRA_DEACTIVATED_REASON)
                    mHtmlText.addTextLine("Deactivated ($reason).<br>")
                    mIsFirstApduSequence = true
                }
            }
        }
    }

    fun setupServices(vararg components: ComponentName) {
        Log.d(TAG, "setupServices")
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
        Log.d(TAG, "emulateServiceSetupFinished")
        mHandler.postDelayed({ listener.onServiceSetupFinished(result) }, 200)
    }

    protected fun getParsedApduHtmlText(cmd: ByteArray): String {
        return if (cmd.size > 5 && cmd[1] == 0xA4.toByte() && cmd[2] == 0x04.toByte()) {
            val sb = StringBuilder()
            val aidLen = cmd[4].toInt()
            sb.append(Util.toHexString(cmd, 0, 5))
            sb.append("<u>")
            sb.append(Util.toHexString(cmd, 5, aidLen))
            sb.append("</u>")
            sb.append(Util.toHexString(cmd, 5 + aidLen, cmd.size - (5 + aidLen)))
            sb.toString()
        } else {
            Util.toHexString(cmd)
        }
    }

    private val mHceServiceSetupListener = HceServiceManager.HceServiceSetupListener { result ->
        mEnabledServices = HceServiceManager.getEnabledServices() as ArrayList<ComponentName>
        dismissDialog()
        outputEnabledServices()
        outputExpectedApdus()
    }

    private fun onApduSequenceComplete(component: ComponentName, duration: Long) {
        Log.e(TAG, "onApduSequenceComplete")
        if (component == TransportService1.COMPONENT) {

            showSuccessDialog()
        }
    }

    private fun onApduSequenceError() {
        Log.e(TAG, "An error occurred.")
    }

    private fun showSuccessDialog() {
        showSuccessDialog(mHtmlText)
    }

    private fun outputEnabledServices() {
        mHtmlText.addSubject1("Enabled Services:")
        Log.d(TAG, "mEnabledServices num=${mEnabledServices.size}")

        for (service in mEnabledServices) {
            val sb = StringBuilder()
            sb.append(mHtmlText.getText("\t\tHost", HtmlText.TRANS_GREEN1))
            sb.append(mHtmlText.getText(", other", HtmlText.TRANS_GREEN1))
            sb.append(mHtmlText.getText(", ${Util.TRANSPORT_AID}", HtmlText.TRANS_GREEN1))
            sb.append(mHtmlText.getText(", requiresUnlock", HtmlText.TRANS_GREEN1))
            mHtmlText.addTextLine(sb.toString().trim())
        }
        mHtmlText.addTextLine("")
    }

    private fun outputExpectedApdus() {
        val apduList = SeqSetList()
        TransportService1.appendSeqSet(apduList)
        val list = apduList.toArray()

        mHtmlText.addSubject1("Expected APDU Sequence:")
        for (seqSet in list) {
            mHtmlText.addTextLineYellow("Recv: ${seqSet.cmdBytes?.let { getParsedApduHtmlText(it) }}")
            mHtmlText.addText("Send: ${Util.toHexString(seqSet.resBytes)}")
            mHtmlText.addTextLine(" - ${seqSet.serviceName}")
        }
        mHtmlText.addTextLine("")
    }

}