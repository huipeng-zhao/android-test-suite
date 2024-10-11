package com.example.cameratest.utils

import android.content.Context
import android.graphics.Bitmap
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

class ThumbnailUtil {

    fun generateThumbnail(context: Context, bitmap: Bitmap, onImageSaved: (Bitmap, ByteArray) -> Unit) {
        val thumbnail = Bitmap.createScaledBitmap(
            bitmap, (bitmap.width * 0.1).toInt(),
            (bitmap.height * 0.1).toInt(), true
        )
        saveThumbnail(context, thumbnail)
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        onImageSaved(bitmap, byteArray)
    }

    private fun saveThumbnail(context: Context, thumbnail: Bitmap) {
        val thumbnailFile = File(
            context.externalMediaDirs.first(),
            "thumb_${System.currentTimeMillis()}.jpg"
        )

        val fos = FileOutputStream(thumbnailFile)
        thumbnail.compress(Bitmap.CompressFormat.JPEG, 90, fos)
        fos.flush()
        fos.close()
    }
}