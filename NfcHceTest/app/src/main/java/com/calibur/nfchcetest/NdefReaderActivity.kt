package com.calibur.nfchcetest

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException
import java.nio.charset.Charset

class NdefReaderActivity : TestActivityBase() {

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var ndefMessage: MyTextView
    private lateinit var mHtmlText: HtmlText

    companion object {
        const val TAG = "NdefReaderActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_reader_ndef)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reader_ndef)) { v: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        initView()

    }

    private fun initView() {
        supportActionBar?.apply {
            title = getString(R.string.nfc_reader_ndef_emulator)
        }
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "NFC is disabled", Toast.LENGTH_SHORT).show()
        }
        ndefMessage = findViewById(R.id.ndef_message)
        mHtmlText = HtmlText(this, ndefMessage, R.id.scroll)
    }

    override fun onResume() {
        super.onResume()
        val pendingIntent = Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val nfcPendingIntent =
            PendingIntent.getActivity(this, 0, pendingIntent, PendingIntent.FLAG_UPDATE_CURRENT)
        val intentFiltersArray = arrayOf<IntentFilter>()
        val techListArray = arrayOf(arrayOf<String>(IsoDep::class.java.name))
        nfcAdapter.enableForegroundDispatch(
            this,
            nfcPendingIntent,
            intentFiltersArray,
            techListArray
        )
    }

    override fun onPause() {
        super.onPause()
        nfcAdapter.disableForegroundDispatch(this)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        val isoDep = IsoDep.get(tag)

        if (isoDep != null) {
            try {
                isoDep.connect()
                val selectAidCommand = Util.hexStringToBytes(Util.buildSelectApdu(Util.NDEF_AID))
                val response = isoDep.transceive(selectAidCommand)
                if (response.isNotEmpty()) {
                    Log.d(TAG, "select aid response: $response")
                    parseNdefMessage(response)
                }
            } catch (e: IOException) {
                Log.e(TAG, "Read Tag Error: $e")
            } finally {
                isoDep.close()
            }
        }
    }

    private fun parseNdefMessage(response: ByteArray) {
        try {
            val ndefMessage = NdefMessage(response)
            Log.d(TAG, "Parsed NDEF message: ${ndefMessage.records.size} records")

            for (ndefRecord in ndefMessage.records) {
                val payload = ndefRecord.payload
                when (ndefRecord.tnf) {
                    NdefRecord.TNF_WELL_KNOWN -> {
                        if (NdefRecord.RTD_URI.contentEquals(ndefRecord.type)) {//URI
                            val uri = String(payload, 1, payload.size - 1)
                            Log.d(TAG, "URL: $uri")
                            mHtmlText.addTextLine("URL: $uri")
                        } else if (NdefRecord.RTD_TEXT.contentEquals(ndefRecord.type)) {//Text
                            val langLength = payload[0].toInt()
                            val text =
                                String(payload, 1 + langLength, payload.size - 1 - langLength)
                            Log.d(TAG, "Text: $text")
                            mHtmlText.addTextLine("Text: $text")
                        }
                    }

                    NdefRecord.TNF_EXTERNAL_TYPE -> {//custom
                        val customType = String(ndefRecord.type)
                        val customData = String(payload, Charset.forName("UTF-8"))
                        Log.d(TAG, "Custom Type: $customType, Data: $customData")
                        mHtmlText.addTextLine("Custom Type: $customType, Data: $customData")
                    }

                    else -> {
                        Log.d(TAG, "Unknown record type")
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse NDEF message", e)
        }
    }

}