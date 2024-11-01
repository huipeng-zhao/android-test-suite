package com.calibur.nfchcetest

import android.content.ComponentName
import android.util.Log
import java.io.Serializable

class SeqSetList : Serializable {
    private val mList = ArrayList<SeqSet>()

    fun addAll(cmd: Array<String>, res: Array<String>, service: ComponentName) {
        for (i in cmd.indices) {
            mList.add(SeqSet(cmd[i], res[i], Util.getClassNameOnly(service.className)))
        }
    }

    fun toArray(): Array<SeqSet> {
        return mList.toTypedArray()
    }

    override fun toString(): String {
        val sb = StringBuilder()
        for (seqSet in mList) {
            sb.append(seqSet.toString()).append("\n")
        }
        return sb.toString()
    }

    inner class SeqSet(
        var cmd: String?,
        var res: String?, // description res
        var desc: String?, // regexp to handle the actual result as Passed
        var resRegexp: String?, // description of resRegexp
        var descResRegexp: String?,
        var serviceName: String?
    ) : Serializable {
        constructor(cmd: String?, res: String?, serviceName: String?) : this(
            cmd,
            res,
            null,
            null,
            null,
            serviceName
        )

        val cmdBytes: ByteArray?
            get() = Util.hexStringToBytes(cmd)

        val resBytes: ByteArray?
            get() = Util.hexStringToBytes(res)

        val isRegisteredVerifyFunction: Boolean
            get() = resRegexp != null

        fun isTestPassed(actualRes: ByteArray?): Boolean {
            if (resRegexp == null) {
                return false
            }
            val actualResStr = Util.toHexStringTrim(actualRes)
            Log.d(TAG, "actualRes=$actualResStr")
            Log.d(TAG, "resRegexp=$resRegexp")
            return actualResStr!!.matches(resRegexp!!.toRegex())
        }

        override fun toString(): String {
            return "$cmd/$res/$serviceName/$resRegexp"
        }
    }

    companion object {
        private const val TAG = "SeqSetList"
    }
}
