package com.example.media

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

enum class FileType {
    AUDIO, IMAGE, VIDEO
}

data class FileInfo(
    var path: String,
    var type: FileType,
    var timestamp: Long,
    var bytes: Int,
    val id: Long,
    val displayName: String,
    val dateAdded: Date,
    val contentUri: Uri,
    val bitmap: Bitmap?
)

open class MediaManager() {

    val TAG = "MediaManager"
    val SHORT_EDGE_720 = 720

    fun generateFileName(type: String, extension: String): String {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        return "$type-$timestamp.$extension"
    }

    open fun newContentValues(): ContentValues {
        val contentValues = ContentValues()
        Log.d(TAG, "newContentValues: ")
        contentValues.apply {
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }

        return contentValues
    }

    suspend fun listFiles(context: Context): List<FileInfo> {
        val images = mutableListOf<FileInfo>()
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.MIME_TYPE,
                MediaStore.MediaColumns.SIZE,
                MediaStore.MediaColumns.DATE_MODIFIED
            )

            val selection = "${MediaStore.MediaColumns.DATA} LIKE ?"
            val selectionArgs = arrayOf(
                "%/DCIM/%"
            )

            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

            context.contentResolver.query(
                MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                projection,
                selection,
                selectionArgs,
                sortOrder
            )?.use { cursor ->

                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                val dateModifiedColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
                val displayNameColumn =
                    cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
                val mimeTypeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val sizeIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val dateModifiedIndex = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_MODIFIED)

                while (cursor.moveToNext()) {

                    val id = cursor.getLong(idColumn)
                    val dateAdded =
                        Date(TimeUnit.SECONDS.toMillis(cursor.getLong(dateModifiedColumn)))
                    val displayName = cursor.getString(displayNameColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        id
                    )

                    val mimeType = cursor.getString(mimeTypeIndex)
                    val size = cursor.getLong(sizeIndex)
                    val dateModified = cursor.getLong(dateModifiedIndex)
                    val type = when (mimeType) {
                        "audio/aac" -> FileType.AUDIO
                        "image/jpeg" -> FileType.IMAGE
                        "video/mp4" -> FileType.VIDEO
                        else -> throw IllegalArgumentException("Unsupported mime type: $mimeType")
                    }
                    val uri = when (type) {
                        FileType.IMAGE -> MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                        FileType.AUDIO -> MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
                        FileType.VIDEO -> MediaStore.Video.Media.EXTERNAL_CONTENT_URI
                    }
                    val fileUri = Uri.withAppendedPath(uri, id.toString())
                    var bitmap: Bitmap?
                    if (displayName.endsWith(".mp4")) {
                        val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(
                            contentUri, "r"
                        )
                        bitmap = getThumbnailJpeg(pfd?.fileDescriptor)
                    } else {
                        bitmap = getBitmapFromUri(contentUri, context)
                    }
                    val image = FileInfo(fileUri.toString(), type, dateModified, size.toInt(), id, displayName, dateAdded, contentUri, bitmap)
                    images += image
                }
            }
        }

        return images
    }

    fun openInputStream(context: Context, path: String): InputStream? {
        val uri = Uri.parse(path)
        return context.contentResolver.openInputStream(uri)
    }

    fun openOutputStream(context: Context, path: String): OutputStream? {
        val uri = Uri.parse(path)
        return context.contentResolver.openOutputStream(uri)
    }

    suspend fun remove(context: Context, image: FileInfo) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.delete(
                    image.contentUri,
                    "${MediaStore.Images.Media._ID} = ?",
                    arrayOf(image.id.toString())
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    suspend fun remove(context: Context, id: Long, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                context.contentResolver.delete(
                    uri,
                    "${MediaStore.Images.Media._ID} = ?",
                    arrayOf(id.toString())
                )
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun getThumbnailJpeg(fd: FileDescriptor?): Bitmap {
        var bitmap: Bitmap? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(fd)
            bitmap = retriever.getFrameAtTime(-1)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        val originalWidth = bitmap!!.width
        val originalHeight = bitmap.height
        val aspectRatio: Float = originalWidth.toFloat() / originalHeight.toFloat()
        val (targetWidth, targetHeight) = if (originalWidth < originalHeight) {
            Pair(SHORT_EDGE_720, (SHORT_EDGE_720 / aspectRatio).toInt())
        } else {
            Pair((SHORT_EDGE_720 * aspectRatio).toInt(), SHORT_EDGE_720)
        }

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        return scaledBitmap
    }

    @Throws(IOException::class)
    fun getBitmapFromUri(uri: Uri, context: Context): Bitmap? {
        val contentResolver = context.contentResolver

        val inputStream = contentResolver.openInputStream(uri)

        val bitmap = BitmapFactory.decodeStream(inputStream)

        inputStream?.close()
        return bitmap
    }
}