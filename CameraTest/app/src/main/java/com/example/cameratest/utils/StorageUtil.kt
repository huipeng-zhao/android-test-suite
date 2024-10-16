package com.example.cameratest.utils

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
import android.widget.Toast
import com.example.cameratest.camera.CameraController.Companion.SHORT_EDGE
import com.example.cameratest.data.MediaStoreImage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext
import java.io.FileDescriptor
import java.io.IOException
import java.io.OutputStream
import java.util.Date
import java.util.concurrent.TimeUnit


class StorageUtil {
    private val TAG = "StorageUtil"
    suspend fun saveMediaToStorage(context: Context, bitmap: Bitmap, name: String) {
        withContext(IO) {
            val filename = "$name.jpg"
            var fos: OutputStream? = null
//            val imagesDir =
//                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM)
//            val image = File(imagesDir, filename).also { fos = FileOutputStream(it) }
//            Intent(Intent.ACTION_MEDIA_SCANNER_SCAN_FILE).also { mediaScanIntent ->
//                mediaScanIntent.data = Uri.fromFile(image)
//                context.sendBroadcast(mediaScanIntent)
//            }
            context.contentResolver?.also { resolver ->

                val contentValues = ContentValues().apply {

                    put(MediaStore.MediaColumns.DISPLAY_NAME, filename)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpg")
                    put(
                        MediaStore.MediaColumns.RELATIVE_PATH,
                        Environment.DIRECTORY_DCIM
                    )
                }
                val imageUri: Uri? =
                    resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

                fos = imageUri?.let {
                    with(resolver) { openOutputStream(it) }
                }
            }

            fos?.use {
                val success = async(IO) {
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, it)
                }
                if (success.await()) {
                    withContext(Dispatchers.Main) {
                        Toast.makeText(context, "Saved Successfully", Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
        }
    }

    suspend fun queryImages(context: Context): List<MediaStoreImage> {
        val images = mutableListOf<MediaStoreImage>()
        withContext(Dispatchers.IO) {
            val projection = arrayOf(
                MediaStore.Files.FileColumns._ID,
                MediaStore.Files.FileColumns.DISPLAY_NAME,
                MediaStore.Files.FileColumns.DATE_ADDED
            )

            val selection = "${MediaStore.Files.FileColumns.DATA} LIKE ?"
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

                Log.i(TAG, "Found ${cursor.count} images")
                while (cursor.moveToNext()) {

                    val id = cursor.getLong(idColumn)
                    val dateModified =
                        Date(TimeUnit.SECONDS.toMillis(cursor.getLong(dateModifiedColumn)))
                    val displayName = cursor.getString(displayNameColumn)

                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
                        id
                    )
                    var bitmap: Bitmap?
                    if (displayName.endsWith(".mp4")) {
                        val pfd: ParcelFileDescriptor? = context.contentResolver.openFileDescriptor(
                            contentUri, "r")
                        bitmap = createVideoThumbnailBitmap(pfd?.fileDescriptor)
                    } else {
                        bitmap = getBitmapFromUri(contentUri, context)
                    }

                    val image = MediaStoreImage(id, displayName, dateModified, contentUri, bitmap)
                    images += image

                    // For debugging, we'll output the image objects we create to logcat.
                    Log.v(TAG, "Added image: $image")
                }
            }
        }

        Log.v(TAG, "Found ${images.size} images")
        return images
    }

    suspend fun performDeleteImage(context: Context, image: MediaStoreImage) {
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

    suspend fun performDeleteImage(context: Context, id:Long, uri: Uri) {
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

    suspend fun getOldestImages(context: Context, count: Int = 10): List<Pair<Long, Uri>> {
        val oldestImages = mutableListOf<Pair<Long, Uri>>()
        withContext(Dispatchers.IO) {
            try {
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val selection = "${MediaStore.Images.Media.DATA} LIKE ?"
                val selectionArgs = arrayOf(
                    "%/DCIM/%"
                )
                val sortOrder = "${MediaStore.Images.Media.DATE_TAKEN} ASC"

                context.contentResolver.query(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    projection,
                    selection,
                    selectionArgs,
                    sortOrder
                )?.use { cursor ->

                    val idColumnIndex = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext() && oldestImages.size < count) {
                        val id = cursor.getLong(idColumnIndex)
                        val contentUri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                        oldestImages.add(id to contentUri)
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
        return oldestImages
    }


    @Throws(IOException::class)
    fun getBitmapFromUri(uri: Uri, context: Context): Bitmap? {
        val contentResolver = context.contentResolver

        val inputStream = contentResolver.openInputStream(uri)

        val bitmap = BitmapFactory.decodeStream(inputStream)

        inputStream?.close()
        return bitmap
    }

    fun createVideoThumbnailBitmap(fd: FileDescriptor?): Bitmap {
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
            Pair(SHORT_EDGE, (SHORT_EDGE / aspectRatio).toInt())
        } else {
            Pair((SHORT_EDGE * aspectRatio).toInt(), SHORT_EDGE)
        }

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
        return scaledBitmap
    }

    fun uriToByteArray(uri: Uri, context: Context): ByteArray? {
        val contentResolver = context.contentResolver

        val inputStream = contentResolver.openInputStream(uri)
        val byteArray = inputStream?.readBytes()
        inputStream?.close()
        return byteArray
    }

    fun getAvailableStorageSize(): Long {
        val freeSpace = Environment.getDataDirectory().freeSpace
        //MB
        return freeSpace / 1000 / 1000
    }

    fun getTotalStorageSize(): Long {
        val totalSpace = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM).totalSpace
        //MB
        return totalSpace / 1000 / 1000
    }

    fun isStorageAvailable(): Pair<Boolean, Int> {
        val availableStorageSize = getAvailableStorageSize()
        val totalStorageSize = getTotalStorageSize()
        val availablePercent =
            availableStorageSize.toDouble() / totalStorageSize.toDouble() * 100
        val available = availablePercent.toInt() >= 10
        return available to availablePercent.toInt()
    }
}