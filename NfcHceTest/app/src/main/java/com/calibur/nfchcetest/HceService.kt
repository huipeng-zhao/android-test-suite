package com.calibur.nfchcetest

import android.content.ComponentName
import android.content.Intent
import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import android.util.Log
import java.util.Arrays

abstract class HceService : HostApduService() {
    companion object {
        private const val TAG = "NfcHceTest_HceService"

        const val STATE_IDLE = 0
        const val STATE_IN_PROGRESS = 1
        const val STATE_FAILED = 2
    }

    private var commandApdus: Array<String>? = null
    private var responseApdus: Array<String>? = null
    private var apduIndex = 0
    private var state = STATE_IDLE
    private var startTime: Long = 0

    private lateinit var serviceName: String

    fun initialize(commandApdus: Array<String>, responseApdus: Array<String>, serviceName: String) {
        this.commandApdus = commandApdus
        this.responseApdus = responseApdus
        this.serviceName = serviceName
    }

    override fun onDeactivated(reason: Int) {
        Log.d(TAG, "$serviceName#onDeactivated: $reason")
        apduIndex = 0
        state = STATE_IDLE
        val intent = Intent(Util.ACTION_DEACTIVATED).apply {
            putExtra(
                Util.EXTRA_DEACTIVATED_REASON,
                when (reason) {
                    DEACTIVATION_LINK_LOSS -> "Link loss"
                    DEACTIVATION_DESELECTED -> "De-selected"
                    else -> "Unknown: $reason"
                }
            )
        }
        sendBroadcast(intent)
    }

    open fun getHceServiceComponent(): ComponentName {
        return getHceServiceComponent()
    }

    fun onApduSequenceComplete() {
        val completionIntent = Intent(Util.ACTION_SEQUENCE_COMPLETE).apply {
            putExtra(Util.EXTRA_COMPONENT, getHceServiceComponent())
            putExtra(Util.EXTRA_DURATION, System.currentTimeMillis() - startTime)
        }
        sendBroadcast(completionIntent)
    }

    fun onApduSequenceError() {
        Log.d(TAG, "$serviceName#APDU SEQ ERROR")
        val errorIntent = Intent(Util.ACTION_SEQUENCE_ERROR)
        sendBroadcast(errorIntent)
    }

    override fun processCommandApdu(arg0: ByteArray, arg1: Bundle?): ByteArray? {
        Log.d(TAG, "processCommandApdu:<< start}")
        if (state == STATE_FAILED) {
            return null
        }

        Log.d(TAG, "$serviceName#processCommandApdu:<< ${Util.toHexString(arg0)}")
        sendApduEventLog(false, arg0)

        if (state == STATE_IDLE) {
            state = STATE_IN_PROGRESS
            startTime = System.currentTimeMillis()
        }

        if (apduIndex >= commandApdus?.size ?: 0) {
            Log.d(TAG, "Ignoring command APDU; protocol complete.")
            return null
        } else {
            val isSelectApdu =
                arg0.size >= 10 && arg0[0] == 0x00.toByte() && arg0[1] == 0xA4.toByte() && arg0[2] == 0x04.toByte()
            var expectedApdu = Util.hexStringToBytes(commandApdus!![apduIndex])
            Log.d(TAG, "isSelectApdu: $isSelectApdu")

            if (!isSelectApdu) {
                val isLargeDataTest =
                    arg0.size > 2 && arg0[0] == 0x99.toByte() && arg0[1] == 0x99.toByte()
                if (isLargeDataTest) {
                    expectedApdu = arg0 // echo
                    responseApdus!![apduIndex] = Util.toHexString(arg0) // echo
                    Log.d(TAG, "This is LargeDataTest.")
                }
            }

            var isAidMatched = false
            if (isSelectApdu) {
                val aid0 = arg0.copyOfRange(5, 5 + arg0[4])
                val aid1 = expectedApdu?.copyOfRange(5, 5 + (expectedApdu?.get(4) ?: 0))
                Log.d(TAG, "aid0: ${Util.toHexString(aid0)}")
                Log.d(TAG, "aid1: ${Util.toHexString(aid1)}")
                if (aid1 != null) {
                    if (aid0.size >= aid1.size) {
                        isAidMatched = Util.toHexString(aid0).startsWith(Util.toHexString(aid1))
                    }
                }
                Log.d(TAG, "isAidMatched $isAidMatched")
            }

            if (isSelectApdu && !isAidMatched) {
                Log.d(TAG, "Unexpected select APDU: ${Util.toHexString(arg0)}")
                onApduSequenceError()
                return null
            } else if (!isSelectApdu && !Arrays.equals(expectedApdu, arg0)) {
                Log.d(TAG, "Unexpected command APDU: ${Util.toHexString(arg0)}")
                onApduSequenceError()
                return null
            } else {
                val responseApdu = Util.hexStringToBytes(responseApdus!![apduIndex])
                Log.d(TAG, "processCommandApdu:>> ${Util.toHexString(responseApdu)}")
                if (responseApdu != null) {
                    sendApduEventLog(true, responseApdu)
                }
                apduIndex++
                if (apduIndex == commandApdus!!.size) {
                    onApduSequenceComplete()
                }
                return responseApdu
            }
        }
    }

    protected fun sendApduEventLog(isSend: Boolean, data: ByteArray) {
        if (!serviceName.contains("Throughput")) {
            val intent =
                Intent(if (isSend) Util.ACTION_SEQ_LOG_SEND else Util.ACTION_SEQ_LOG_RECV).apply {
                    putExtra(Util.EXTRA_REQ_LOG, data)
                    putExtra(Util.EXTRA_WHO, serviceName)
                }
            sendBroadcast(intent)
        }
    }
}