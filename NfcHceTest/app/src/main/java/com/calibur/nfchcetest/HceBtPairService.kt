package com.calibur.nfchcetest

import android.bluetooth.BluetoothAdapter
import android.content.ComponentName
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log

class HceBtPairService : HostApduService() {
    companion object {
        val COMPONENT = ComponentName(Util.PACKAGE, HceBtPairService::class.java.name)
        const val TAG = "HceBtPairService"
    }

    private lateinit var bluetoothAddress: String

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HceBtPairService Service Created")
        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()

        if (bluetoothAdapter != null) {
//            bluetoothAddress = bluetoothAdapter.address
            bluetoothAddress = "22:22:9F:1D:8B:17"
            Log.d(TAG, "Local Device BT Mac: $bluetoothAddress")
        } else {
            Log.e(TAG, "Not Support BT!")
        }
    }

    override fun processCommandApdu(commandApdu: ByteArray, extras: Bundle?): ByteArray {
        val strApdu = Util.toHexStringTrim(commandApdu)
        Log.d(TAG, "Received APDU command: $strApdu")
        val expectApdu = Util.buildSelectApdu(Util.BT_PAIR_AID)
        if (strApdu == expectApdu) {
            return createBluetoothPairingNdef()
        }

        return ByteArray(0)
    }

    private fun createBluetoothPairingNdef(): ByteArray {
        val handoverSelectRecord = createHandoverSelectRecord(bluetoothAddress.toByteArray())
        val ndefMessage = NdefMessage(handoverSelectRecord)
        Log.d(TAG, "result apdu: ${Util.toHexString(ndefMessage.toByteArray())}")
        return ndefMessage.toByteArray()
    }

    private fun createHandoverSelectRecord(macAddressBytes: ByteArray): NdefRecord {
        return NdefRecord(
            NdefRecord.TNF_WELL_KNOWN,
            NdefRecord.RTD_HANDOVER_SELECT,
            ByteArray(0),
            macAddressBytes
        )
    }

    override fun onDeactivated(reason: Int) {
        Log.d(HceNdefService.TAG, "HCE Service Deactivated, reason: $reason")
    }
}