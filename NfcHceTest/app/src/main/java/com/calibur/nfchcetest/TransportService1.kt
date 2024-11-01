package com.calibur.nfchcetest

import android.content.ComponentName

class TransportService1 : HceService() {
    companion object {
        val COMPONENT = ComponentName(Util.PACKAGE, TransportService1::class.java.name)

        val APDU_COMMAND_SEQUENCE = arrayOf(
            Util.buildSelectApdu(Util.TRANSPORT_AID),
            "68656c6c6F20776F7264" // "hello world" in hex
        )

        val APDU_RESPOND_SEQUENCE = arrayOf(
            "80CA9000",
            "6f6b9000" // "ok" in hex
        )

        fun appendSeqSet(list: SeqSetList) {
            list.addAll(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE, COMPONENT)
        }
    }

    init {
        initialize(APDU_COMMAND_SEQUENCE, APDU_RESPOND_SEQUENCE, "TransportService1")
    }

    override fun getHceServiceComponent(): ComponentName {
        return COMPONENT
    }
}
