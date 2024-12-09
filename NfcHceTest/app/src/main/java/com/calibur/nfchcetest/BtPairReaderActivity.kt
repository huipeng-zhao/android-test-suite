package com.calibur.nfchcetest

import android.Manifest
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.annotation.RequiresPermission
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.io.IOException


class BtPairReaderActivity : TestActivityBase() {

    companion object {
        const val TAG = "BtPairReaderActivity"
    }

    private lateinit var nfcAdapter: NfcAdapter
    private lateinit var btPairMessage: MyTextView
    private lateinit var mHtmlText: HtmlText

    private val REQUEST_BLUETOOTH_PERMISSION = 1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_bt_pair_reader)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.reader_bt_pair)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_SCAN
            ) != PackageManager.PERMISSION_GRANTED
            || ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT),
                REQUEST_BLUETOOTH_PERMISSION
            )
        }

        initView()
    }

    private fun initView() {
        supportActionBar?.apply {
            title = getString(R.string.nfc_bt_pair_reader)
        }
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        if (!nfcAdapter.isEnabled) {
            Toast.makeText(this, "NFC is disabled", Toast.LENGTH_SHORT).show()
        }
        btPairMessage = findViewById(R.id.bt_pair_message)
        mHtmlText = HtmlText(this, btPairMessage, R.id.scroll)


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

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        val tag: Tag = intent?.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        val isoDep = IsoDep.get(tag)

        if (isoDep != null) {
            try {
                isoDep.connect()
                val selectAidCommand = Util.hexStringToBytes(Util.buildSelectApdu(Util.BT_PAIR_AID))
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

    @RequiresPermission(Manifest.permission.BLUETOOTH_SCAN)
    private fun parseNdefMessage(response: ByteArray) {
        try {
            val ndefMessage = NdefMessage(response)
            val ndefRecords = ndefMessage.records
            for (record in ndefRecords) {
                if (record.tnf == NdefRecord.TNF_WELL_KNOWN && record.type.contentEquals(NdefRecord.RTD_HANDOVER_SELECT)) {
                    val bluetoothMacBytes = record.payload
                    val bluetoothMacAddress = String(bluetoothMacBytes, Charsets.UTF_8)
                    Log.d(TAG, "Received Bluetooth MAC Address: $bluetoothMacAddress")
                    initiateBluetoothPairing(bluetoothMacAddress)
                }
            }
        } catch (e: IOException) {
            Log.e(TAG, "Failed to parse NDEF message", e)
        }
    }

    @RequiresPermission(Manifest.permission.BLUETOOTH_CONNECT)
    private fun initiateBluetoothPairing(macAddress: String) {
        Log.d(TAG, "initiateBluetoothPairing: identifier = $macAddress")
        val bluetoothAdapter: BluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        if (bluetoothAdapter.isEnabled) {
            val device = bluetoothAdapter.getRemoteDevice(macAddress)
            val bondState = device.createBond()
            if (bondState) {
                Log.d(TAG, "BT Pair Success.")
                Toast.makeText(this, "Pairing with $macAddress", Toast.LENGTH_SHORT).show()
            } else {
                Log.e(TAG, "Pairing failed or already paired.")
                Toast.makeText(this, "Pairing failed or already paired", Toast.LENGTH_SHORT).show()
            }
        } else {
            Log.e(TAG, "BluetoothAdapter: isEnabled = false")
        }

    }

}