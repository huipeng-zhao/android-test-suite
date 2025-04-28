package  com.example.videorecord

import ai.looki.companion.devo_main.ALL_DIRS
import ai.looki.companion.devo_main.DIR_AUDIO_USER
import ai.looki.companion.devo_main.DIR_BASE
import ai.looki.companion.devo_main.DIR_IMAGE_AUTO_IMAGE
import ai.looki.companion.devo_main.DIR_IMAGE_AUTO_THUMB
import ai.looki.companion.devo_main.DIR_IMAGE_USER_IMAGE
import ai.looki.companion.devo_main.DIR_IMAGE_USER_THUMB
import ai.looki.companion.devo_main.DIR_VIDEO_AUTO_AUDIO
import ai.looki.companion.devo_main.DIR_VIDEO_AUTO_IMU
import ai.looki.companion.devo_main.DIR_VIDEO_AUTO_THUMB
import ai.looki.companion.devo_main.DIR_VIDEO_AUTO_TIME
import ai.looki.companion.devo_main.DIR_VIDEO_AUTO_VIDEO
import ai.looki.companion.devo_main.DIR_VIDEO_USER_AUDIO
import ai.looki.companion.devo_main.DIR_VIDEO_USER_IMU
import ai.looki.companion.devo_main.DIR_VIDEO_USER_THUMB
import ai.looki.companion.devo_main.DIR_VIDEO_USER_TIME
import ai.looki.companion.devo_main.DIR_VIDEO_USER_VIDEO
import android.app.usage.StorageStatsManager
import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.FileObserver
import android.os.Handler
import android.os.Looper
import android.os.StatFs
import android.os.storage.StorageManager
import android.provider.MediaStore
import android.util.Log
import androidx.core.content.getSystemService
import androidx.core.text.isDigitsOnly
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.nio.file.Files
import java.nio.file.attribute.BasicFileAttributes
import java.time.Duration
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import kotlin.math.abs

data class MediaFileInfo(
    // Common Info.
    val uri: Uri,
    val displayName: String,
    val size: Long,
    val dateAdded: LocalDateTime,
    val mimeType: String,
    // For Image and Video.
    val width: Int,         // -1 means not exist
    val height: Int,        // -1 means not exist
    // For Video and Audio.
    val duration: Duration, // Duration.ZERO means not exist
)

@Suppress("unused")
fun ContentResolver.listMediaFiles(queryUri: Uri, relativePath: String?): List<MediaFileInfo> {
    val mediaFiles = mutableListOf<MediaFileInfo>()

    val projection = arrayOf(
        // Common Info.
        MediaStore.MediaColumns._ID,
        MediaStore.MediaColumns.DISPLAY_NAME,
        MediaStore.MediaColumns.SIZE,
        MediaStore.MediaColumns.DATE_ADDED, // in seconds, from 1970-01-01T00:00:00Z
        MediaStore.MediaColumns.MIME_TYPE,
        // For Image and Video.
        MediaStore.MediaColumns.WIDTH,
        MediaStore.MediaColumns.HEIGHT,
        // For Video and Audio.
        MediaStore.MediaColumns.DURATION, // in milliseconds
    )
    val selection = relativePath?.let { "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?" }
    val selectionArgs = relativePath?.let { arrayOf("$it/%") }
    val sortOrder = "${MediaStore.MediaColumns.DATE_ADDED} DESC"

    query(queryUri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
        val idColumn = cursor.getColumnIndex(MediaStore.MediaColumns._ID)
        val nameColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        val sizeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.SIZE)
        val dateColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_ADDED)
        val mimeColumn = cursor.getColumnIndex(MediaStore.MediaColumns.MIME_TYPE)
        val widthColumn = cursor.getColumnIndex(MediaStore.MediaColumns.WIDTH)
        val heightColumn = cursor.getColumnIndex(MediaStore.MediaColumns.HEIGHT)
        val durationColumn = cursor.getColumnIndex(MediaStore.MediaColumns.DURATION)

        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn)
            val name = cursor.getString(nameColumn)
            val size = cursor.getLong(sizeColumn)
            val date = cursor.getLong(dateColumn)
            val mime = cursor.getString(mimeColumn)
            // image & video
            val width = if (widthColumn == -1) -1 else cursor.getInt(widthColumn)
            val height = if (heightColumn == -1) -1 else cursor.getInt(heightColumn)
            // audio & video
            val duration = if (durationColumn == -1) 0 else cursor.getLong(durationColumn)

            // Only append id for if the queryUri has no id.
            val uri = if (queryUri.lastPathSegment?.isDigitsOnly() == true) queryUri else ContentUris.withAppendedId(queryUri, id)
            mediaFiles.add(MediaFileInfo(
                uri = uri,
                displayName = name,
                size = size,
                dateAdded = Instant.ofEpochSecond(date).atZone(ZoneId.systemDefault()).toLocalDateTime(),
                mimeType = mime,
                width = width,
                height = height,
                duration = Duration.ofMillis(duration)
            ))
        }
    }
    return mediaFiles
}

@Suppress("unused")
enum class MediaFileType(val mime: String, val ext: String, val dir: File, val format: String) {
    USER_IMAGE(      "image/jpeg", "jpg", DIR_IMAGE_USER_IMAGE, "%d"),
    USER_IMAGE_THUMB("image/jpeg", "jpg", DIR_IMAGE_USER_THUMB, "%d"),
    AUTO_IMAGE(      "image/jpeg", "jpg", DIR_IMAGE_AUTO_IMAGE, "%d"),
    AUTO_IMAGE_THUMB("image/jpeg", "jpg", DIR_IMAGE_AUTO_THUMB, "%d"),
    USER_VIDEO(      "video/mp4",  "mp4", DIR_VIDEO_USER_VIDEO, "%d"),
    USER_VIDEO_THUMB("image/jpeg", "jpg", DIR_VIDEO_USER_THUMB, "%d"),
    USER_VIDEO_AUDIO("audio/aac",  "m4a", DIR_VIDEO_USER_AUDIO, "%d"),
    USER_VIDEO_IMU  ("text/csv",  "gcsv", DIR_VIDEO_USER_IMU,   "%d"),
    USER_VIDEO_TIME ("text/csv",   "csv", DIR_VIDEO_USER_TIME,  "%d"),
    AUTO_VIDEO(      "video/mp4",  "mp4", DIR_VIDEO_AUTO_VIDEO, "%d"),
    AUTO_VIDEO_THUMB("image/jpeg", "jpg", DIR_VIDEO_AUTO_THUMB, "%d"),
    AUTO_VIDEO_AUDIO("audio/aac",  "m4a", DIR_VIDEO_AUTO_AUDIO, "%d"),
    AUTO_VIDEO_IMU  ("text/csv",  "gcsv", DIR_VIDEO_AUTO_IMU,   "%d"),
    AUTO_VIDEO_TIME ("text/csv",   "csv", DIR_VIDEO_AUTO_TIME,  "%d"),
    USER_AUDIO(      "audio/aac",  "m4a", DIR_AUDIO_USER,       "%d"),
}

class MediaFileManager private constructor() {
    companion object {
        private val TAG = MediaFileManager::class.simpleName

        @Volatile
        private var INSTANCE: MediaFileManager? = null

        fun getInstance(): MediaFileManager {
            return INSTANCE ?: synchronized(this) { INSTANCE ?: MediaFileManager().also { INSTANCE = it } }
        }
    }

    private val myUserImageCounter = MediaFileCounter(MediaFileType.USER_IMAGE)
    private val myAutoImageCounter = MediaFileCounter(MediaFileType.AUTO_IMAGE)
    private val myUserVideoCounter = MediaFileCounter(MediaFileType.USER_VIDEO)
    private val myUserAudioCounter = MediaFileCounter(MediaFileType.USER_AUDIO)
    private val myUserImageThumbCounter = MediaFileCounter(MediaFileType.USER_IMAGE_THUMB)
    private val myAutoImageThumbCounter = MediaFileCounter(MediaFileType.AUTO_IMAGE_THUMB)
    private val myUserVideoThumbCounter = MediaFileCounter(MediaFileType.USER_VIDEO_THUMB)
    private val myUserVideoAudioCounter = MediaFileCounter(MediaFileType.USER_VIDEO_AUDIO)
    private val myAutoVideoCounter = MediaFileCounter(MediaFileType.AUTO_VIDEO) // for auto video files
    private val myAutoVideoThumbCounter = MediaFileCounter(MediaFileType.AUTO_VIDEO_THUMB)

    private val myFileObserver = object : FileObserver(DIR_BASE, DELETE_SELF or MOVE_SELF) {
        override fun onEvent(event: Int, relativePath: String?) {
            when (event) {
                DELETE_SELF, MOVE_SELF -> { // the DIR_BASE gone.
                    Log.i(TAG, "${DIR_BASE.path} is gone, stop watching and reset counts.")
                    this.stopWatching()
                    // Create the DIR_BASE and start monitoring again.
                    Handler(Looper.getMainLooper()).post { initiate() }
                }
            }
        }
    }

    private fun initiate() {
        try {
            ALL_DIRS.forEach { dir -> dir.mkdirs() }
            if (DIR_BASE.exists()) {
                Log.i(TAG, "initiate: start watching ${DIR_BASE.path}")
                myFileObserver.startWatching()
                myUserImageCounter.startWatching()
                myAutoImageCounter.startWatching()
                myUserVideoCounter.startWatching()
                myUserAudioCounter.startWatching()
                myUserImageThumbCounter.startWatching()
                myAutoImageThumbCounter.startWatching()
                myUserVideoThumbCounter.startWatching()
                myUserVideoAudioCounter.startWatching() // for user video audio
                myAutoVideoCounter.startWatching() // for auto video files
                myAutoVideoThumbCounter.startWatching() // for auto video thumb
            } else {
                Log.e(TAG, "initiate: mkdirs(${DIR_BASE.path}) failed")
            }
        } catch (e: Exception) {
            Log.e(TAG, "initiate failed: ${e.message}")
        }
    }

    init {
        initiate()
    }

    fun newFile(type: MediaFileType, timestampMs: Long, customizedFilename: String? = null): File? {
        try {
            var timestampFilename = ""
            if(type == MediaFileType.USER_IMAGE_THUMB || type == MediaFileType.AUTO_IMAGE_THUMB){
                timestampFilename = "$timestampMs.${type.ext}.$timestampMs.${type.ext}"
            }else{
                timestampFilename = "${type.format.format(timestampMs)}.${type.ext}"
            }
            return File(type.dir, customizedFilename ?: timestampFilename).also {
                it.parentFile?.mkdirs() // create the missing parents for the file.
            }
        } catch (e: Exception) {
            Log.e(TAG, "newFile failed: ${e.message}")
        }
        return null
    }

    fun deleteAll() {
        MediaFileType.entries.forEach { type ->
            try {
                type.dir.listFiles()?.forEach {
                    try {
                        it.delete()
                    } catch (e: Exception) {
                        Log.e(TAG, "deleteAll: delete(${it.path}) failed, ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "deleteAll: listFiles($type) failed, ${e.message}")
            }
        }
    }

    //获取本地文件中最早的和最晚的，给app展示
    fun getUserFileMinMaxModifiedTimes(): Pair<Instant?, Instant?> {
        var earliest: Instant? = null
        var latest: Instant? = null
        val folders: List<File> = MediaFileType.entries.map { it.dir }
        for (dir in folders) {
            dir.walkTopDown().filter { it.isFile }.forEach { file ->
                val attrs = Files.readAttributes(file.toPath(), BasicFileAttributes::class.java)
                val creationTime = attrs.creationTime().toInstant()

                if (earliest == null || creationTime.isBefore(earliest)) {
                    earliest = creationTime
                }
                if (latest == null || creationTime.isAfter(latest)) {
                    latest = creationTime
                }
            }
        }
        return Pair(earliest, latest)
    }

    val userImageCount: StateFlow<Int> = myUserImageCounter.count
    val autoImageCount: StateFlow<Int> = myAutoImageCounter.count
    val userVideoCount: StateFlow<Int> = myUserVideoCounter.count
    val userAudioCount: StateFlow<Int> = myUserAudioCounter.count
    val userImageThumbCount: StateFlow<Int> = myUserImageThumbCounter.count
    val autoImageThumbCount: StateFlow<Int> = myAutoImageThumbCounter.count
    val userVideoThumbCount: StateFlow<Int> = myUserVideoThumbCounter.count
    val userVideoAudioCount: StateFlow<Int> = myUserVideoAudioCounter.count
    val autoVideoCount: StateFlow<Int> = myAutoVideoCounter.count
    val autoVideoThumbCount: StateFlow<Int> = myAutoVideoThumbCounter.count
}

class MediaFileCounter(val type: MediaFileType) {
    companion object {
        private val TAG = MediaFileCounter::class.simpleName
    }

    private val myCount = MutableStateFlow(0)

    private val myObserver = object : FileObserver(type.dir, CREATE or MOVED_TO or DELETE or MOVED_FROM or DELETE_SELF or MOVE_SELF) {
        override fun onEvent(event: Int, relativePath: String?) {
            val file = relativePath?.let { File(type.dir, it) }
            when (event) {
                CREATE, MOVED_TO -> {
                    if (file?.isOfType() == true) {
                        myCount.value += 1
                        Log.d(TAG, "${type.name}.count=${myCount.value}, File Created: ${file.path}")
                    } else {
                        Log.w(TAG, "onEvent: ${file?.path} is not of type ${type.name}")
                    }
                }
                DELETE, MOVED_FROM -> {
                    if (file?.isOfType() == true) {
                        myCount.value -= 1
                        Log.d(TAG, "${type.name}.count=${myCount.value}, File Deleted: ${file.path}")
                    } else {
                        Log.w(TAG, "onEvent: ${file?.path} is not of type ${type.name}")
                    }
                }
                DELETE_SELF, MOVE_SELF -> {
                    myCount.value = 0
                    stopWatching()
                    Log.d(TAG, "${type.name} Directory Deleted: ${type.dir}")
                }
            }
        }
    }

    private fun File.isOfType(): Boolean {
        return this.extension == type.ext && this.parent == type.dir.path
    }

    private fun countFiles(directory: File): Int {
        if (!directory.exists() || !directory.isDirectory) { return 0 }
        try {
            return directory.listFiles { _, name -> name.endsWith("."+type.ext) }?.size ?: 0
        } catch (e: Exception) {
            Log.e(TAG, "countFiles: ${e.message}", e)
            return 0
        }
    }

    fun startWatching() {
        if (type.dir.exists()) {
            myCount.value = countFiles(type.dir)
            Log.d(TAG, "startWatching: ${type.dir.path}, count=${myCount.value}")
            myObserver.startWatching()
        } else {
            Log.e(TAG, "startWatching: ${type.dir.path} does not exist")
        }
    }

    @Suppress("unused")
    fun countNow(): Int { myCount.value = countFiles(type.dir); return myCount.value }

    val count: StateFlow<Int> = myCount
}

@Suppress("unused")
class StorageMonitor private constructor(context: Context) {
    data class Capacity(val total: Long, val userdata: Long, val available: Long) {
        override fun toString(): String {
            if (this == INVALID_CAPACITY) return "Invalid Capacity"
            val avail = available.toDouble().toReadable()
            return "Avail=%s, Free=%.1f%%".format(avail, freePercent())
        }
        fun toDetailString(): String {
            return "${toString()}, total=$total, userdata=$userdata, available=$available"
        }
        fun freePercent(): Double {
            return available.toDouble() / userdata * 100
        }
    }

    companion object {
        private val TAG = StorageMonitor::class.simpleName

        @Volatile
        private var INSTANCE: StorageMonitor? = null

        // singleton
        fun getInstance(context: Context?): StorageMonitor {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: context?.let { StorageMonitor(context.applicationContext).also { INSTANCE = it } }
                    ?: throw IllegalStateException("StorageMonitor2 not initialized")
            }
        }

        // it's ok for calculation, but it's ridiculous for real capacity.
        val INVALID_CAPACITY = Capacity(1, 1, 0)

        fun updateCapacity(): Capacity {
            return INSTANCE?.updateCapacity() ?: INVALID_CAPACITY
        }
    }

    private val myStorageStatsManager = context.getSystemService<StorageStatsManager>()!!

    private val myCapacity = MutableStateFlow(getCapacity())
    val capacity: StateFlow<Capacity> = myCapacity

    fun updateCapacity(): Capacity {
        return getCapacity().also { myCapacity.value = it }
    }

    private fun getCapacity(): Capacity {
        val total = myStorageStatsManager.getTotalBytes(StorageManager.UUID_DEFAULT)
        val stat = StatFs(Environment.getExternalStorageDirectory().path)
        val userdata = stat.totalBytes
        val available = stat.availableBytes
        return Capacity(total, userdata, available)
    }

    init {
        // Start monitoring in a non-main-thread coroutine.
        CoroutineScope(Dispatchers.IO + SupervisorJob()).launch {
            var lastFree = 0.0
            while (isActive) {
                delay(60_000L) // update the capacity once in every minute.
                updateCapacity().run {
                    val free = freePercent()
                    // Print log only when the free space changes more than 1%.
                    if (abs(lastFree - free) >= 0.1) {
                        lastFree = free
                        Log.v(TAG, "StorageCapacity: ${toDetailString()}")
                    }
                }
            }
        }
    }
}

fun Double.toReadable(suffix: String = "B"): String {
    val gb = 1024L * 1024L * 1024L
    val mb = 1024L * 1024L
    val kb = 1024L
    return when {
        this.toInt() >= gb -> "%.1fG${suffix}".format(this / gb)
        this.toInt() >= mb -> "%.1fM${suffix}".format(this / mb)
        this.toInt() >= kb -> "%.1fK${suffix}".format(this / kb)
        else -> "${this.toInt()}${suffix}"
    }
}