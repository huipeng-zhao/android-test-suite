package com.example.ancs

object Util {

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

    fun bytesToHexString(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}