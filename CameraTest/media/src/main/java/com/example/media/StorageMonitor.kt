package com.example.media

import android.os.Environment
import android.os.StatFs
import java.text.DecimalFormat

class StorageMonitor(
    private val minFreePercentage: Double = Double.NaN,
    private val minFreeSpace: Long = -1,
) {
    val status: Triple<Boolean, Double, Long>
        get() {
            val externalStorageDir = Environment.getExternalStorageDirectory()
            val stat = StatFs(externalStorageDir.path)

            val totalBytes = stat.totalBytes
            val freeBytes = stat.availableBytes
            val freePercentage = (freeBytes.toDouble() / totalBytes) * 100

            val isAvailable = when {
                minFreePercentage.isNaN() && minFreeSpace < 0 -> false
                !minFreePercentage.isNaN() && minFreeSpace >= 0 ->
                    freePercentage >= minFreePercentage || freeBytes >= minFreeSpace
                !minFreePercentage.isNaN() -> freePercentage >= minFreePercentage
                else -> freeBytes >= minFreeSpace
            }

            return Triple(isAvailable, DecimalFormat("#.##").format(freePercentage).toDouble() , freeBytes)
        }
}