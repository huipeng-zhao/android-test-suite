package com.calibur.nfchcetest

import HceServiceManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.ColorDrawable
import android.nfc.NfcAdapter
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

class MainActivity : TestActivityBase() {
    private var mDialogFragment: MyDialogFragment? = null
    private val TAG = "NfcHceTest_MainActivity"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initView()
    }

    private fun initView() {
        supportActionBar!!.setBackgroundDrawable(ColorDrawable(getColor(R.color.bg1_common)))
        val readerBt = findViewById<Button>(R.id.reader_bt)
        readerBt.setOnClickListener { v: View? ->
            val intent = Intent(this@MainActivity, ReaderActivity::class.java)
            startActivity(intent)
        }

        val emulatorBt = findViewById<Button>(R.id.emulator_bt)
        emulatorBt.setOnClickListener { v: View? ->
            showDialog(
                MyDialogFragment.Companion.DIALOG_ID_PROGRESS,
                getString(R.string.nfc_hce_please_wait),
                getString(R.string.nfc_hce_setting_up)
            )
            HceServiceManager.disableAllServices(
                this@MainActivity
            ) { result: Boolean? ->
                dismissDialog()
                val intent = Intent(this@MainActivity, EmulatorActivity::class.java)
                startActivity(intent)
            }
        }

        val nfcSupported = isNfcSupported(this)
        if (!nfcSupported) {
            showAlertDialog("Error", "Not Support NFC!")
            Log.d(TAG, "Not Support NFC!")
        }

        if (!isHceSupported(this)) {
            showAlertDialog("Error", "Not Support NFC HostEmulator!")
            Log.d(TAG, "Not Support NFC HostEmulator!")
        }
    }

    private fun isNfcSupported(context: Context): Boolean {
        val nfcAdapter = NfcAdapter.getDefaultAdapter(context)
        return nfcAdapter != null && nfcAdapter.isEnabled
    }

    private fun isHceSupported(context: Context): Boolean {
        return if (isNfcSupported(context)) {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_NFC_HOST_CARD_EMULATION)
        } else {
            false
        }
    }

    private fun showDialog(id: Int, title: String, message: String) {
        runOnUiThread {
            try {
                if (mDialogFragment != null) {
                    mDialogFragment!!.dismiss()
                }
                mDialogFragment = MyDialogFragment.Companion.newInstance(id, title, message, null)
                mDialogFragment!!.isCancelable = false
                mDialogFragment!!.show(fragmentManager, MyDialogFragment.Companion.DIALOG_TAG)
            } catch (e: RuntimeException) {
            } catch (e: Exception) {
            }
        }
    }

    override fun dismissDialog() {
        runOnUiThread {
            try {
                if (mDialogFragment != null) {
                    mDialogFragment!!.dismiss()
                    mDialogFragment = null
                }
            } catch (e: RuntimeException) {
            } catch (e: Exception) {
            }
        }
    }
}