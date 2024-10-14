package com.android.ware.daemon.updater

import android.util.Log
import java.io.BufferedReader
import java.io.File
import java.io.IOException
import java.io.InputStreamReader
import java.util.Locale
import java.util.stream.Collectors
import java.util.zip.ZipFile

class UpdateParser {
    companion object {
        private const val TAG = "UpdateParser"
        private const val PAYLOAD_BIN_FILE = "payload.bin"
        private const val PAYLOAD_PROPERTIES = "payload_properties.txt"
        private const val FILE_URL_PREFIX = "file://"
        private const val ZIP_FILE_HEADER = 30
    }

    var mOffset: Long = 0L
    var mSize: Long = 0L
    var mProps: Array<String>? = null
    var mUrl: String? = null
    var isValid: Boolean = false

    @Throws(IOException::class)
    fun parse(file: File) {
        var payloadOffset: Long = 0
        var payloadSize: Long = 0
        var payloadFound = false
        var props: Array<String>? = null

        ZipFile(file).use { zipFile ->
            val entries = zipFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                val fileSize = entry.compressedSize
                if (!payloadFound) {
                    payloadOffset += (ZIP_FILE_HEADER + entry.name.length).toLong()
                    if (entry.extra != null) {
                        payloadOffset += entry.extra.size.toLong()
                    }
                }

                if (entry.isDirectory) {
                    continue
                } else if (entry.name == PAYLOAD_BIN_FILE) {
                    payloadSize = fileSize
                    payloadFound = true
                } else if (entry.name == PAYLOAD_PROPERTIES) {
                    BufferedReader(
                        InputStreamReader(zipFile.getInputStream(entry))
                    ).use { buffer ->
                        props = buffer.lines().collect(Collectors.toList()).toTypedArray()
                    }
                }
                if (!payloadFound) {
                    payloadOffset += fileSize
                }
                Log.d(TAG, String.format("Entry %s", entry.name))
            }
        }
        mOffset = payloadOffset
        mSize = payloadSize
        mProps = props
        mUrl = FILE_URL_PREFIX + file.absolutePath
        isValid = (mOffset >= 0 && mSize > 0 && mProps != null)
    }
}