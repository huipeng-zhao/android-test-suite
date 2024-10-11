package com.example.cameratest.data

import android.graphics.Bitmap
import android.net.Uri
import java.util.Date

data class MediaStoreImage(
    val id: Long,
    val displayName: String,
    val dateAdded: Date,
    val contentUri: Uri,
    val bitmap: Bitmap?
)