package com.example.media

import android.content.ContentValues
import android.provider.MediaStore

class AudioMediaManager() : MediaManager() {
    override fun newContentValues(): ContentValues {
        return super.newContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, generateFileName("audio", "aac"))
            put(MediaStore.MediaColumns.MIME_TYPE, "audio/aac")
        }
    }
}