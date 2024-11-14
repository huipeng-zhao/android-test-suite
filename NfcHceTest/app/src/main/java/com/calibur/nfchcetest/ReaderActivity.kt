package com.calibur.nfchcetest

import android.content.ComponentName
import android.graphics.drawable.ColorDrawable
import android.nfc.NfcAdapter
import android.nfc.NfcAdapter.ReaderCallback
import android.nfc.Tag
import android.nfc.tech.IsoDep
import android.os.Bundle
import android.util.Log
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.calibur.nfchcetest.SeqSetList.SeqSet
import java.io.IOException
import java.util.Arrays
import java.util.Objects

class ReaderActivity : TestActivityBase(), ReaderCallback {
    private var mTextView: MyTextView? = null
    private var mHtmlText: HtmlText? = null

    private var mAdapter: NfcAdapter? = null

    private var mSeqSet: Array<SeqSet>? = null;
    private var mDestService: ComponentName? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        this.enableEdgeToEdge()
        setContentView(R.layout.activity_common)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.scroll)) { v: View, insets: WindowInsetsCompat ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        initView()
        initData()
    }

    private fun initView() {
        supportActionBar?.apply {
            title = getString(R.string.nfc_hce_reader_tests)
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
        mAdapter = NfcAdapter.getDefaultAdapter(this)
        val apduList = SeqSetList()
        TransportService1.Companion.appendSeqSet(apduList)
        mSeqSet = apduList.toArray()
        for (i in mSeqSet!!.indices) {
            Log.d(TAG, String.format("seq[%d].cmd=[%s]", i, mSeqSet?.get(i)?.cmd))
            Log.d(TAG, String.format("seq[%d].res=[%s]", i, mSeqSet?.get(i)?.res))
        }
        mDestService = TransportService1.COMPONENT

        val flags = (NfcAdapter.FLAG_READER_NFC_A
                or NfcAdapter.FLAG_READER_NFC_B
                or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK)
        mAdapter?.enableReaderMode(this, this, flags, null)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume()")
        outputTestDescription()
    }

    private fun outputTestDescription() {
        var sb = StringBuilder()
        if (mTextView!!.text.toString().isEmpty()) {
            mHtmlText!!.addSubject1("Enabled Reader Modes:")
            sb.append("NFC_A, ")
            sb.append("NFC_B, ")
            sb.append("SKIP_NDEF_CHECK, ")
            mHtmlText!!.addTextLine("$sb<br>")
            Log.d(TAG, "Enabled Reader Modes: $sb")

            mHtmlText!!.addSubject1("Destination services:")
            sb = StringBuilder()
            sb.append(Util.getClassNameOnly(mDestService!!.className)).append("<br>")
            mHtmlText!!.addTextLine(sb.toString())

            mHtmlText!!.addSubject1("Expected APDU Sequence:")
            for (seqSet in mSeqSet!!) {
                if (seqSet?.cmdBytes?.size!! < 1000) {
                    mHtmlText!!.addTextLine("Send: " + getParsedApduHtmlText(seqSet?.cmdBytes))
                    mHtmlText!!.addTextYellow("Recv: " + Util.toHexString(seqSet?.resBytes))
                }
                mHtmlText!!.addTextLine(" - " + seqSet!!.serviceName, HtmlText.Companion.YELLOW1)
            }
            mHtmlText!!.addTextLine("")
            mHtmlText!!.addSubject1("Actual APDU Sequence:")
        }
    }

    protected fun getParsedApduHtmlText(cmd: ByteArray?): String? {
        if (cmd!!.size > 5 && cmd[1] == 0xA4.toByte() && cmd[2] == 0x04.toByte()) {
            val sb = StringBuilder()
            val aidLen = cmd[4].toInt()
            sb.append(Util.toHexString(cmd, 0, 5))
            sb.append("<u>")
            sb.append(Util.toHexString(cmd, 5, aidLen))
            sb.append("</u>")
            sb.append(Util.toHexString(cmd, 5 + aidLen, cmd.size - (5 + aidLen)))
            return sb.toString()
        } else {
            return Util.toHexString(cmd)
        }
    }

    override fun onTagDiscovered(tag: Tag) {
        var success = true
        val timeList = LongArray(mSeqSet!!.size)
        var i = 0
        val ERR_MSG1 = "An unexpected response APDU was received," +
                " or no APDUs were received at all."
        val ERR_MSG2 = "IOException (did you keep the devices in range?)."
        var logOp = ""
        var errorMsg = ERR_MSG2
        dismissDialog()

        val isoDep = IsoDep.get(tag)
        if (isoDep == null) {
            showAlertDialog("Unexpected tag", "This is not IsoDep")
            return
        }

        mHtmlText!!.addSubject2("Tag Discovered", HtmlText.Companion.TRANS_BLUE2, true)

        try {
            logOp = "connect:"
            isoDep.connect()
            mHtmlText!!.addTextLine("Connected.")
            Log.d(TAG, "Connected.")

            Arrays.fill(timeList, 0)

            for (apduSet in mSeqSet!!) {
                val cmd = Util.hexStringToBytes(apduSet!!.cmd)
                mHtmlText!!.addTextLine("Send: " + getParsedApduHtmlText(cmd))
                Log.d(TAG, "Send: " + Util.toHexString(cmd))

                val apduStartTime = System.currentTimeMillis()
                logOp = "transceive:"
                val response = isoDep.transceive(cmd)
                val apduEndTime = System.currentTimeMillis()
                timeList[i] = apduEndTime - apduStartTime
                i++

                Log.d(TAG, "Recv: " + Util.toHexString(response))
                parseResult(response)
                if (apduSet.descResRegexp != null && !apduSet.descResRegexp!!.isEmpty()) {
                    mHtmlText!!.addTextLine(apduSet.descResRegexp, HtmlText.Companion.GREEN1)
                }

                mHtmlText!!.addTextLine("(in " + (apduEndTime - apduStartTime) + " ms)")

                val expectedResponse = Util.hexStringToBytes(
                    apduSet.res
                )
                if (apduSet.isRegisteredVerifyFunction) {
                    Log.d(TAG, "isRegisteredVerifyFunction")
                    if (!apduSet.isTestPassed(response)) {
                        putUnexpectedApduResponseMsg(response)
                        success = false
                    }
                } else {
                    if (!response.contentEquals(expectedResponse)) {
                        putUnexpectedApduResponseMsg(response)
                        success = false
                    }
                }
                mHtmlText!!.addTextLine("")
                if (!success) {
                    errorMsg = ERR_MSG1
                }
            }
        } catch (e: IOException) {
            success = false
            Log.e(TAG, logOp + e.toString())
            mHtmlText!!.addTextLineRed(
                """
    $ERR_MSG2
    ${e.message}
    """.trimIndent()
            )
        }
    }

    private fun isSuccess(res: ByteArray?): Boolean {
        if (res == null || res.size < 2) {
            return false
        }
        val sw1sw2 = String.format("%02X%02X", res[res.size - 2], res[res.size - 1])
        return sw1sw2.matches("9000|9100".toRegex())
    }

    private fun parseResult(res: ByteArray?) {
        val RES = arrayOf(
            arrayOf<Any>("9000", "Success"),
            arrayOf<Any>("6281", "The returned data may be erroneous."),
            arrayOf<Any>(
                "6282",
                "Fewer bytes than specified by the Le parameter could be read, since the end of the file was encountered first."
            ),
            arrayOf<Any>("6283", "The selected file is reversibly blocked (invalidated)."),
            arrayOf<Any>(
                "6284",
                "The file control information (FCI) is not structured in accordance with ISO/IEC7816-4."
            ),  //              {"62xx", "Warning; state of non-volatile memory not changed."},
            //              {"63Cx", "The counter has reached the value x (0 = x = 15) (the exact significance depends on the command)."},
            //              {"63xx", "Warning; state of non-volatile memory changed."},
            //              {"64xx", "Execution error; state of non-volatile memory not changed."},
            arrayOf<Any>(
                "6581",
                "Memory error (e.g. during a write operation)."
            ),  //              {"65xx", "Execution error; state of non-volatile memory changed."},
            arrayOf<Any>("6700", "Length incorrect."),
            arrayOf<Any>("6800", "Functions in the class byte not supported (general)."),
            arrayOf<Any>("6881", "Logical channels not supported."),
            arrayOf<Any>("6882", "Secure messaging not supported."),
            arrayOf<Any>("6900", "Command not allowed (general)"),
            arrayOf<Any>("6981", "Command incompatible with file structure."),
            arrayOf<Any>("6982", "Security state not satisfied."),
            arrayOf<Any>("6983", "Authentication method blocked."),
            arrayOf<Any>("6984", "Referenced data reversibly blocked (invalidated)."),
            arrayOf<Any>("6985", "Usage conditions not satisfied."),
            arrayOf<Any>("6986", "Command not allowed (no EF selected)."),
            arrayOf<Any>("6987", "Expected secure messaging data objects missing."),
            arrayOf<Any>("6988", "Secure messaging data objects incorrect."),  /* add */
            arrayOf<Any>("6999", "Applet selection failed."),
            arrayOf<Any>("6A00", "Incorrect P1 or P2 parameters (general)."),
            arrayOf<Any>("6A80", "Parameters in the data portion are incorrect."),
            arrayOf<Any>("6A81", "Function not supported."),
            arrayOf<Any>("6A82", "File not found or Currently selected."),
            arrayOf<Any>("6A83", "Record not found."),
            arrayOf<Any>("6A84", "Insufficient memory."),
            arrayOf<Any>("6A85", "Lc inconsistent with TLV structure"),
            arrayOf<Any>("6A86", "Incorrect P1or P2 parameter."),
            arrayOf<Any>("6A87", "Lc inconsistent with P1 or P2."),
            arrayOf<Any>("6A88", "Referenced data not found."),
            arrayOf<Any>(
                "6B00",
                "Parameter 1 or 2 incorrect."
            ),  //              {"6Cxx", "Bad length value in Le; "xx  is the correct length."},
            arrayOf<Any>("6D00", "Command (instruction) not supported."),
            arrayOf<Any>("6E00", "Class not supported."),
            arrayOf<Any>("6F00", "No precise diagnosis."),
            arrayOf<Any>(
                "9000",
                "Command successfully executed."
            ),  //              {"920x", "Writing to EEPROM successful after "x  attempts."},
            arrayOf<Any>("9210", "Insufficient memory."),
            arrayOf<Any>("9240", "Writing to EEPROM not successful."),
            arrayOf<Any>("9400", "No EF selected."),
            arrayOf<Any>("9402", "Address range exceeded."),
            arrayOf<Any>(
                "9404",
                "FID not found, record not found or comparison pattern not found."
            ),
            arrayOf<Any>("9408", "Selected file type does not match command."),
            arrayOf<Any>("9802", "No PIN defined."),
            arrayOf<Any>("9804", "Access conditions not satisfied, authentication failed."),
            arrayOf<Any>("9835", "ASK RANDOM or GIVE RANDOM not executed."),
            arrayOf<Any>("9840", "PIN verification not successful."),
            arrayOf<Any>(
                "9850",
                "INCREASE or DECREASE could not be executed because a limit has been reached."
            ),  //              {"9Fxx", "Command successfully executed; "xx  bytes of data are available and can be requested using GET RESPONSE."},
            arrayOf<Any>("9100", "OPERATION_OK"),
            arrayOf<Any>("910C", "NO_CHANGES"),
            arrayOf<Any>("910E", "OUT_OF_EEPROM_ERROR"),
            arrayOf<Any>("911C", "ILLEGAL_COMMAND_CODE"),
            arrayOf<Any>("911E", "INTEGRITY_ERROR"),
            arrayOf<Any>("9140", "NO_SUCH_KEY"),
            arrayOf<Any>("917E", "LENGTH_ERROR"),
            arrayOf<Any>("919D", "PERMISSION_DENIED"),
            arrayOf<Any>("919E", "PARAMETER_ERROR"),
            arrayOf<Any>("91A0", "APPLICATION_NOT_FOUND"),
            arrayOf<Any>("91A1", "APPL_INTEGRITY_ERROR"),
            arrayOf<Any>("91AE", "AUTHENTICATION_ERROR"),
            arrayOf<Any>("91AF", "ADDITIONAL_FRAME"),
            arrayOf<Any>("91BE", "BOUNDARY_ERROR"),
            arrayOf<Any>("91C1", "PICC_INTEGRITY_ERROR"),
            arrayOf<Any>("91CA", "COMMAND_ABORTED"),
            arrayOf<Any>("91CD", "PICC_DISABLED_ERROR"),
            arrayOf<Any>("91CE", "COUNT_ERROR"),
            arrayOf<Any>("91DE", "DUPLICATE_ERROR"),
            arrayOf<Any>("91EE", "EEPROM_ERROR"),
            arrayOf<Any>("91F0", "FILE_NOT_FOUND"),
            arrayOf<Any>("91F1", "FILE_INTEGRITY_ERROR"),
        )


        if (res == null || res.size < 2) {
            mHtmlText!!.addTextLineRed(Util.toHexString(res) + " invalid response")
            return
        }

        val sw1sw2 = String.format("%02X%02X", res[res.size - 2], res[res.size - 1])
        var desc = "???"
        for (i in RES.indices) {
            if (sw1sw2 == RES[i][0] as String) {
                desc = RES[i][1] as String
                break
            }
        }

        mHtmlText!!.addText("Recv: " + Util.toHexString(res), HtmlText.Companion.YELLOW1)
        if (isSuccess(res)) {
            mHtmlText!!.addTextLineBlue(" <i>($desc)</i>")
        } else {
            if ("???" == desc) {
                mHtmlText!!.addTextLine("")
            } else {
                mHtmlText!!.addTextLineRed(" <i>($desc)</i>")
            }
        }
        return
    }

    private fun putUnexpectedApduResponseMsg(response: ByteArray) {
        Log.d(TAG, "Unexpected APDU response: " + Util.getHexBytes("", response))
        mHtmlText!!.addTextLineRed("An unexpected response APDU was received.")
    }

    companion object {
        private const val TAG = "NfcHceTest_ReaderActivity"
    }
}
