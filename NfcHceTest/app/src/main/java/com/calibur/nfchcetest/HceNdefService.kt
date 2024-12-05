package com.calibur.nfchcetest

import android.content.ComponentName
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.cardemulation.CardEmulation
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.nio.charset.Charset

class HceNdefService : HostApduService() {
    companion object {
        val COMPONENT = ComponentName(Util.PACKAGE, HceNdefService::class.java.name)
        const val TAG = "HceNdefService"
    }

    override fun onCreate() {
        super.onCreate()
        Log.d(TAG, "HceNdefService Service Created")
    }

    override fun processCommandApdu(apdu: ByteArray, extras: Bundle?): ByteArray? {
        Log.d(TAG, "Received APDU command: ${apdu.joinToString()}")
        if (apdu[0] == 0x00.toByte() && apdu[1] == 0xA4.toByte()) {
            val ndefMessage = generateNdefMessage()

            return ndefMessage.toByteArray()
        }

        return ByteArray(0)
    }

    private fun generateNdefMessage(): NdefMessage {
        val uriRecord = NdefRecord.createUri("https://www.baidu.com")
        val textRecord = createTextRecord("您好")
        val customRecord = createCustomRecord("Custom Data Example")

        return NdefMessage(uriRecord, textRecord, customRecord)
    }

    private fun createTextRecord(text: String): NdefRecord {
        val langCode = "en"
        val textBytes = text.toByteArray(Charset.forName("UTF-8"))
        val langBytes = langCode.toByteArray(Charset.forName("UTF-8"))

        val payload = ByteArray(1 + langBytes.size + textBytes.size)
        payload[0] = langBytes.size.toByte()
        System.arraycopy(langBytes, 0, payload, 1, langBytes.size)
        System.arraycopy(textBytes, 0, payload, 1 + langBytes.size, textBytes.size)

        return NdefRecord(NdefRecord.TNF_WELL_KNOWN, NdefRecord.RTD_TEXT, ByteArray(0), payload)
    }

    private fun createCustomRecord(data: String): NdefRecord {
        val customData = data.toByteArray(Charset.forName("UTF-8"))
        return NdefRecord(NdefRecord.TNF_EXTERNAL_TYPE, "com.example.custom".toByteArray(), ByteArray(0), customData)
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "HCE Service Deactivated, reason: $reason")
    }

}