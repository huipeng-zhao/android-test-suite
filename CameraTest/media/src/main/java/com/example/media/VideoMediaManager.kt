package com.example.media

import android.content.ContentValues
import android.provider.MediaStore

class VideoMediaManager() : MediaManager() {

    override fun newContentValues(): ContentValues {
        return super.newContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, generateFileName("video", "mp4"))
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
        }
    }
}