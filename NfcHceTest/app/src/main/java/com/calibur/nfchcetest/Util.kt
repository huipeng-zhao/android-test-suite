package com.calibur.nfchcetest

import android.content.ComponentName
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Vibrator
import android.util.Log

object Util {
    private const val TAG = "NfcHceTest_Util"

    const val PACKAGE: String = "com.calibur.nfchcetest"
    const val ACTION_SEQUENCE_COMPLETE: String = "com.calibur.nfchcetest.ACTION_SEQUENCE_COMPLETE"
    const val ACTION_SEQUENCE_ERROR: String = "com.calibur.nfchcetest.ACTION_SEQUENCE_ERROR"

    const val ACTION_SEQ_LOG_SEND: String = "com.calibur.nfchcetest.ACTION_SEQ_LOG_SEND"
    const val ACTION_SEQ_LOG_RECV: String = "com.calibur.nfchcetest.ACTION_SEQ_LOG_RECV"
    const val EXTRA_REQ_LOG: String = "com.calibur.nfchcetest.EXTRA_REQ_LOG"
    const val EXTRA_WHO: String = "com.calibur.nfchcetest.EXTRA_WHO"

    const val ACTION_DEACTIVATED: String = "com.calibur.nfchcetest.ACTION_DEACTIVATED"
    const val EXTRA_DEACTIVATED_REASON: String = "com.calibur.nfchcetest.EXTRA_DEACTIVATED_REASON"

    const val EXTRA_COMPONENT: String = "component"
    const val EXTRA_DURATION: String = "duration"

    const val TRANSPORT_AID: String = "F001020304"
    const val NDEF_AID: String = "F001020305"
    const val PPSE_AID: String = "325041592E5359532E4444463031"
    const val MC_AID: String = "A0000000041010"

    fun toHexString(data: Byte): String {
        return String.format("%02X", data)
    }

    fun toHexStringTrim(buffer: ByteArray?): String {
        return toHexString(buffer).replace(" ", "")
    }

    fun toHexString(buffer: ByteArray?): String {
        return if (buffer == null) {
            "null"
        } else {
            toHexString(buffer, 0, buffer.size)
        }
    }

    fun toHexString(buffer: ByteArray?, offset: Int): String {
        if (buffer == null) {
            return "null"
        } else {
            val length = buffer.size - offset
            return toHexString(buffer, offset, length)
        }
    }

    fun toHexString(buffer: ByteArray?, offset: Int, length: Int): String {
        if (buffer!!.size == 0) {
            return "empty"
        }

        val work = StringBuilder("")
        if (buffer != null) {
            for (x in 0 until length) {
                work.append(String.format("%02X ", buffer[x + offset]))
                if (x > 200) {
                    work.append("...")
                    break
                }
            }
        }
        return work.toString()
    }

    fun getHexBytes(header: String?, bytes: ByteArray): String {
        val sb = StringBuilder()
        if (header != null) {
            sb.append("$header: ")
        }
        for (b in bytes) {
            sb.append(String.format("%02X ", b))
        }
        return sb.toString()
    }

    fun hexStringToBytes(aid: String?): ByteArray? {
        if (aid.isNullOrEmpty()) {
            return null
        }

        var s = aid.replace(" ", "")
        var len = s.length
        if (len % 2 != 0) {
            s = "0$s"
            len++
        }

        val data = ByteArray(len / 2)
        for (i in s.indices step 2) {
            data[i / 2] =
                ((Character.digit(s[i], 16) shl 4) + Character.digit(s[i + 1], 16)).toByte()
        }
        return data
    }

    fun vibrate(context: Context) {
        val vibrator = context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        val pattern = longArrayOf(0, 100, 50, 100) // OFF/ON/OFF/ON...
        vibrator.vibrate(pattern, -1)
    }

    fun getClassNameOnly(component: ComponentName?): String? {
        return getClassNameOnly(component?.className)
    }

    fun getClassNameOnly(fullClassName: String?): String? {
        if (fullClassName?.indexOf(".") == -1) {
            return fullClassName
        }
        return fullClassName?.substring(fullClassName.lastIndexOf(".") + 1)
    }

    fun buildSelectApdu(aid: String?): String {
        var aid = aid
        val sb = StringBuilder()
        aid = aid!!.replace(" ", "")
        sb.append("00A40400")
        sb.append(String.format("%02X", aid.length / 2))
        sb.append(aid)
        if (aid.equals(PPSE_AID, ignoreCase = true) || aid.equals(MC_AID, ignoreCase = true)) {
            sb.append("00")
        }
        Log.d(TAG, "select apdu: $sb")
        return sb.toString()
    }

    class SoundPlayer(context: Context?) {
        private var mSoundPool: SoundPool? = null
        private val mSound = intArrayOf(-1, -1)
        private var mContext: Context? = null

        init {
            if (mSoundPool == null) {
                mContext = context
                mSoundPool = SoundPool.Builder()
                    .setMaxStreams(1)
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .build()
                mSound[SOUND_SUCCESS] = mSoundPool!!.load(mContext, R.raw.fluorine, 0)
                mSound[SOUND_FAILURE] = mSoundPool!!.load(mContext, R.raw.beryllium, 0)
            }
        }

        fun play(sound: Int) {
            val audio = mContext!!.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            val volumeRate = 0.5f
            mSoundPool!!.play(mSound[sound], volumeRate, volumeRate, 0, 0, 1.0f)
        }

        @Synchronized
        fun release() {
            if (mSoundPool != null) {
                for (sound in mSound) {
                    if (sound != -1) {
                        mSoundPool!!.unload(sound)
                    }
                }
                mSoundPool!!.release()
                mSoundPool = null
            }
        }

        companion object {
            const val SOUND_SUCCESS: Int = 0
            const val SOUND_FAILURE: Int = 1
        }
    }
}
