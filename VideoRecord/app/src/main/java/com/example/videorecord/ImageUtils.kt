package com.example.videorecord

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import java.io.ByteArrayOutputStream

object ImageUtils {
    fun compressYunImageToJpeg(yuvImage: YuvImage, quality: Int): ByteArray {
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), quality, out)
        return out.toByteArray()
    }

    @Suppress("unused")
    fun convertYunImageToBitmap(yuvImage: YuvImage): Bitmap {
        val out = ByteArrayOutputStream()
        yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
        val imageBytes = out.toByteArray()
        return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
    }

    fun convertYuv420ToYuvImage(image: Image, cache: ByteArray): YuvImage {
        val planes = image.planes
        val yPlane = planes[0].buffer
        val uPlane = planes[2].buffer
        val vPlane = planes[1].buffer
        val yStride = planes[0].rowStride
        val uStride = planes[2].rowStride
        val vStride = planes[1].rowStride
        val yStrideSize = yStride * image.height
        val uStrideSize = uStride / 2 * image.height
        yPlane.get(cache, 0, yPlane.remaining())
        uPlane.get(cache, yStrideSize, uPlane.remaining())
        vPlane.get(cache, yStrideSize + uStrideSize, vPlane.remaining())
        val strides = intArrayOf(yStride, uStride, vStride)
        return YuvImage(cache, ImageFormat.NV21, image.width, image.height, strides)
    }
}
