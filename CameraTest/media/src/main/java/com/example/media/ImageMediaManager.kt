package com.example.media

import android.content.ContentValues
import android.provider.MediaStore

class ImageMediaManager() : MediaManager() {
    override fun newContentValues(): ContentValues {
        return super.newContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, generateFileName("image", "jpeg"))
            put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
        }
    }
}