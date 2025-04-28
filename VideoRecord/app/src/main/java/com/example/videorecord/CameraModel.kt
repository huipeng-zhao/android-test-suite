package com.example.videorecord

import android.Manifest
import android.annotation.SuppressLint
import android.app.Application
import android.graphics.ImageFormat
import android.hardware.camera2.*
import android.hardware.camera2.params.OutputConfiguration
import android.hardware.camera2.params.SessionConfiguration
import android.media.Image
import android.media.ImageReader
import android.media.MediaRecorder
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.util.Log
import android.util.Range
import android.util.Size
import android.view.Surface
import androidx.core.content.getSystemService
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.AndroidViewModel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.BufferedWriter
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.io.use
import kotlin.time.Duration.Companion.nanoseconds
import kotlin.time.DurationUnit
import kotlin.time.measureTime
import kotlin.time.measureTimedValue
import kotlin.use


class CameraModel(val context: Application) : AndroidViewModel(context) {

    private val STRIDE_ALIGN = 128
    private val THUMB_SIZE = Size(720, 480)
    private val THUMB_I420_BUF_LEN_MIN = ((THUMB_SIZE.width+STRIDE_ALIGN-1)/STRIDE_ALIGN)*STRIDE_ALIGN*THUMB_SIZE.height*2 // YUV_420_888
    private val THUMB_QUALITY = 80

    private val IMAGE_SIZE = Size(4000, 3000)
    private val IMAGE_JPEG_BUF_DEF_LEN = 8*1024*1024 // 8MB
    private val myGcsvRecorder = GcsvRecorder(context)

    // Pre-allocate buffers to avoid memory allocation in runtime.
    private val THUMB_I420_BUF = ByteArray(THUMB_I420_BUF_LEN_MIN*2) // preserve enough space
    private var IMAGE_JPEG_BUF = ByteArray(IMAGE_JPEG_BUF_DEF_LEN)

    val VIDEO_SIZE = Size(1920, 1080)
    val VIDEO_FPS = 30
    val VIDEO_BITRATE = 5_000_000
    var myVideoRecorder: MediaRecorder? = null
    val myCameraExecutor = Executors.newSingleThreadExecutor() // run Camera2 callbacks.
    var myCameraManager: CameraManager? = null
    val myCallbackExecutor = Executors.newSingleThreadExecutor() // run onThumbnailCompleted callbacks.
    var myCheck3aCount = 0
    private val myIsImageCapturing = MutableStateFlow<Boolean?>(null)
    private val myIsVideoRecording = MutableStateFlow<Boolean?>(null)
    private var myCameraDevice: CameraDevice? = null
    private var myCameraSession: CameraCaptureSession? = null
    private var myCameraId: String? = null
    private val TAG = MainActivity::class.java.simpleName
    var mFileName = MutableStateFlow<File?>(null)
    var mImageData = MutableSharedFlow<ByteArray>(extraBufferCapacity = 10)
    private var myFrameTimeRecorder: BufferedWriter? = null

    init {
        myCameraManager = context.getSystemService<CameraManager>()!!
        myCameraId = getCameraId()
    }

    class ThreadHandler(name: String) {
        private val myThread = HandlerThread(name).apply { start() }
        val h: Handler = Handler(myThread.looper)
    }
    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
    )
    var missingPermissions = MutableStateFlow<List<String>?>(null)
    private val myHandler = ThreadHandler("ImageSaveThread") // run ImageReader.

    fun capturePhoto(   callback: CaptureImageCallback? = null,) {
        if (isBusy()) {
            Log.w(TAG, "captureImage: Camera is busy.")
            return
        }

        val timestampStartCamera = System.currentTimeMillis()
        myIsImageCapturing.value = true
        openCamera { camera ->
            if (camera == null) {
                Log.e(TAG, "openCamera: failed, camera is null")
                return@openCamera
            }

            var isCamera3aConverged = false
            var isThumbnailDone = false
            val surfaces = mutableListOf<Surface>()

            val thumbReader = ImageReader.newInstance(THUMB_SIZE.width, THUMB_SIZE.height, ImageFormat.YUV_420_888, 1)
            val imageReader = ImageReader.newInstance(IMAGE_SIZE.width, IMAGE_SIZE.height, ImageFormat.JPEG, 1)
            val previewRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val captureRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
            val captureCallback = SessionCaptureCallback()
            val previewCallback = SessionCaptureCallback()
            var imageCaptureTimestampMs: Long = 0
            var thumbFile: File? = null
            var thumbExposureTime: Long? = null
            var thumbSensitivity: Int? = null
            var imageFile: File? = null
            var imageExposureTime: Long? = null
            var imageSensitivity: Int? = null

            previewRequest.addTarget(thumbReader.surface)
            previewRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range<Int>(50, 50)) // raise fps to limit the exposure time.
            surfaces.add(thumbReader.surface)

            captureRequest.addTarget(imageReader.surface)
            captureRequest.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, Range<Int>(50, 50)) // raise fps to limit the exposure time.
            surfaces.add(imageReader.surface)

            thumbReader.setOnImageAvailableListener({ reader ->
                if (!isCamera3aConverged || isThumbnailDone) {
                    reader.acquireNextImage()?.close()
                    return@setOnImageAvailableListener
                }
                previewCallback.isFinished = true
                val timestampCaptureBegin = previewCallback.startTimestampMs
                val timestampCaptureEnd = System.currentTimeMillis()
                imageCaptureTimestampMs = timestampCaptureEnd
                reader.acquireNextImage()?.also { image ->
                    val (yuv, timeConvYuv) = measureTimedValue { image.use { ImageUtils.convertYuv420ToYuvImage(it, THUMB_I420_BUF) } }
                    val (jpeg, timeCompress) = measureTimedValue { ImageUtils.compressYunImageToJpeg(yuv, THUMB_QUALITY) }
                    val timestampCompressDone = System.currentTimeMillis()
                    mImageData.tryEmit(jpeg)
                    val thumbnail = ThumbnailInfo(imageCaptureTimestampMs, jpeg, timestampStartCamera, timestampCaptureBegin, timestampCaptureEnd, timestampCompressDone)
                    myCallbackExecutor.execute(Runnable { callback?.onThumbnailComplete(thumbnail) }) // run callback async.
                    val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
                    val fileName = "thumb_$timeStamp"
                    val path =File(context.getExternalFilesDir(Environment.DIRECTORY_DCIM), fileName)
                    thumbFile = saveJpegFile(path.path, fileName, jpeg, jpeg.size, imageCaptureTimestampMs)
                    thumbFile?.also { saveExif(it, imageCaptureTimestampMs, thumbExposureTime, thumbSensitivity) }
                }
                isThumbnailDone = true

                myCameraSession!!.stopRepeating()
                myCameraSession!!.abortCaptures()
                myCameraSession!!.captureSingleRequest(captureRequest.build(), myCameraExecutor, captureCallback)
            }, myHandler.h)

            var isCaptureResultReady = false
            fun saveImageIfReady() {
                imageFile?.also { file ->
                    if (isCaptureResultReady) {
                        saveExif(file, imageCaptureTimestampMs, imageExposureTime, imageSensitivity)
                        callback?.onQualityImageComplete(file, thumbFile, imageCaptureTimestampMs)
                        closeCamera("image capture completed")
                        myIsImageCapturing.value = false
                    }
                }
            }

            imageReader.setOnImageAvailableListener({ reader ->

                imageFile = saveJpegImage(reader.acquireNextImage(), imageCaptureTimestampMs)
                saveImageIfReady()
            }, myHandler.h)

            captureCallback.setOnCaptureResult { session, request, result, timestampMs, failure ->
                if (result != null) {
                    check3aReady(result)
                    imageExposureTime = result[CaptureResult.SENSOR_EXPOSURE_TIME]
                    imageSensitivity = result[CaptureResult.SENSOR_SENSITIVITY]
                } else {
                    closeCamera("image capture failed")
                }
                isCaptureResultReady = true
                saveImageIfReady()
            }

            previewCallback.setOnCaptureResult { session, request, result, timestampMs, failure ->
                failure?.also {
                    if (!isCamera3aConverged) {
                        closeCamera("preview failed")
                    }
                    return@setOnCaptureResult
                }
                if (isCamera3aConverged) return@setOnCaptureResult
                if (!check3aReady(result!!)) return@setOnCaptureResult
                isCamera3aConverged = true
                thumbExposureTime = result[CaptureResult.SENSOR_EXPOSURE_TIME]
                thumbSensitivity = result[CaptureResult.SENSOR_SENSITIVITY]
                // Now the 3A is converged, tell the caller that the capture will begin.
                callback?.onCaptureBegin()
            }

            // Build the config to create the session.
            val config = createSessionConfiguration(surfaces) { success, session ->
                if (!success) return@createSessionConfiguration
                myCameraSession = session
                // Here the session is configured successfully.
                session.setSingleRepeatingRequest(previewRequest.build(), myCameraExecutor, previewCallback)
            }

            // Create and start the session.
            camera.createCaptureSession(config)
        }
    }

    fun newFile(timestampMs: Long, type: Int): File? {
        var videoRecordDir: File? = null
        try {
            val sdcardDir = Environment.getExternalStorageDirectory()

            videoRecordDir = File(sdcardDir, "videorecord")

            if (!videoRecordDir.exists()) {
                val created = videoRecordDir.mkdirs()
                if (!created) {
                    println("Failed to create directory: ${videoRecordDir.absolutePath}")
                    return null
                }
            }

        } catch (e: Exception) {
            e.printStackTrace()
            println("Error creating directory: ${e.message}")
        }

        try {
            val fileDir = videoRecordDir!!.absolutePath
            val fileName = when (type) {
                0 -> "imu$timestampMs.gcsv"
                1 -> "time$timestampMs.csv"
                2 -> "$timestampMs.mp4"
                else -> {
                    println("Invalid type: $type")
                    return null
                }
            }
            val filePath = "$fileDir/$fileName"
            val file = File(filePath)
            return file
        } catch (e: Exception) {
            Log.e(TAG, "newFile failed: ${e.message}")
        }
        return null
    }

    private fun startGcsvRecord(videoRecordTimestampMs: Long) {
        newFile(videoRecordTimestampMs, 0)?.also {
            myGcsvRecorder.startSensor()
            myGcsvRecorder.startRecord(it)
        }
    }

    private fun startTimestampRecord(videoRecordTimestampMs: Long) {
        newFile(videoRecordTimestampMs, 1).also {
            myFrameTimeRecorder = it?.bufferedWriter()
            myFrameTimeRecorder?.write("#,ExposureStartNS,ExposureTimeNS,CaptureDurationNS\n")
        }
    }
    fun startRecording(){
        if (isBusy()) {
            return
        }
        myIsVideoRecording.value = true
        openCamera { camera ->
            if (camera == null) {
                return@openCamera
            }
            try {
                val videoRecordTimestampMs = System.currentTimeMillis()
                @Suppress("DEPRECATION")
                val mediaRecorder = MediaRecorder().apply {
                    val file = newFile(videoRecordTimestampMs, 2)
                    mFileName.tryEmit(file)

                    // input
                    setAudioSource(MediaRecorder.AudioSource.MIC)
                    setVideoSource(MediaRecorder.VideoSource.SURFACE)
                    setOutputFile(file)
                    // output
                    setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
                    // codec
                    setVideoEncodingBitRate(VIDEO_BITRATE)
                    setVideoFrameRate(VIDEO_FPS)
                    setVideoSize(VIDEO_SIZE.width, VIDEO_SIZE.height)
                    setVideoEncoder(MediaRecorder.VideoEncoder.HEVC)
                    setAudioEncoder(MediaRecorder.AudioEncoder.AAC)

                    prepare()
                }
                myVideoRecorder = mediaRecorder // keep the reference.

                val config = createSessionConfiguration(listOf(mediaRecorder.surface)) { success, session ->
                    if (!success) { return@createSessionConfiguration }

                    startTimestampRecord(videoRecordTimestampMs)
                    startGcsvRecord(videoRecordTimestampMs)


                    myCameraSession = session
                    val recordRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply { addTarget(mediaRecorder.surface) }.build()
                    session.setSingleRepeatingRequest(recordRequest, myCameraExecutor, object : CameraCaptureSession.CaptureCallback() {
                        private var frameIndex: Int = 0
                        private var is3aReady = false
                        override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestamp: Long, frameNumber: Long) {
                            if (!is3aReady) return
                        }
                        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
                            if (is3aReady) {
                                val now = SystemClock.elapsedRealtimeNanos()
                                // record the timestamp info.
                                val exposureStartTime = result[CaptureResult.SENSOR_TIMESTAMP]!!
                                val exposureDuration = result[CaptureResult.SENSOR_EXPOSURE_TIME]!!
                                val captureDuration = now - exposureStartTime // from exposure start to capture end.
                                frameIndex++
                                myFrameTimeRecorder?.write("${frameIndex},${exposureStartTime},${exposureDuration},${captureDuration}\n")

                                return
                            }
                            if (!check3aReady(result)) return
                            Log.d("zwjtest " , "3a ready")
                            is3aReady = true
                        }
                        // we only care about the failure, to stop the recording in time.
                        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
                            stopRecording()
                        }
                    })
                    mediaRecorder.start()
                }

                camera.createCaptureSession(config)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun createSessionConfiguration(surfaces: List<Surface>, onSessionConfigured: (Boolean, CameraCaptureSession) -> Unit): SessionConfiguration {
        val sessionStateCallback = object : CameraCaptureSession.StateCallback() {
            override fun onConfigured(session: CameraCaptureSession) {
                try {
                    onSessionConfigured(true, session)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            override fun onConfigureFailed(session: CameraCaptureSession) {
                try {
                    onSessionConfigured(false, session)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
        return SessionConfiguration(
            SessionConfiguration.SESSION_REGULAR,
            surfaces.map { OutputConfiguration(it) },
            myCameraExecutor,
            sessionStateCallback
        )
    }
    fun stopRecording() {
        measureTime {
            try {
                // Actually, the session will be closed automatically when camera closed, we just close it manually.
                myCameraSession?.close()
                myCameraDevice?.close()
                myFrameTimeRecorder?.close()
                myVideoRecorder?.apply {
                    stop()
                    reset()
                    release()
                }
                myGcsvRecorder.close()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                myVideoRecorder = null
                closeCamera()
                myIsVideoRecording.value = false
                myFrameTimeRecorder=null
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun openCamera(onCameraOpened: (CameraDevice?) -> Unit) {
        Log.d(TAG, "openCamera")
        try {
            val cameraStateCallback = object : CameraDevice.StateCallback() {
                override fun onOpened(camera: CameraDevice) {
                    myCameraDevice = camera
                    try {
                        Log.d(TAG, "openCamera onOpened")

                        onCameraOpened(camera)
                    } catch (e: Exception) {
                        closeCamera("openCamera onOpened failed")
                        Log.d(TAG, "openCamera falied $e")
                        e.printStackTrace()
                    }
                }
                override fun onDisconnected(camera: CameraDevice) {
                    closeCamera("openCamera onDisconnected")
                    Log.d(TAG, "onDisconnected ")
                }
                override fun onError(camera: CameraDevice, error: Int) {
                    closeCamera("openCamera onError")
                    Log.d(TAG, "onError ")
                }
            }
            val cameraId = myCameraId ?: run {
                closeCamera("cameraId is null")
                return onCameraOpened(null)
            }
            myCameraManager!!.openCamera(cameraId, myCameraExecutor, cameraStateCallback)
        } catch (e: Exception) {
            closeCamera("openCamera failed")
            e.printStackTrace()
        }
    }

    private fun closeCamera(reason: String? = null) {
        myCameraSession?.close()
        myCameraSession = null
        myCameraDevice?.close()
        myCameraDevice = null
        Log.d(TAG, "close $reason" )
        // Force to release the memory.
        System.gc()
        // Must set these flags at last, to make sure the next action will not overlap.
    }

    private fun isBusy(): Boolean {
        // NOTE: The myIsCameraUsing is async changed by the following 2 state, so it may not be accurate.
        return myIsImageCapturing.value == true || myIsVideoRecording.value == true
    }

    private fun getCameraId(): String? {
        return try {
            myCameraManager!!.cameraIdList[0]
        } catch (e: Exception) {
            Log.e(TAG, "getCameraId", e)
            null
        }
    }

    data class ThumbnailInfo(
        val fileTimestampMs: Long,
        val jpeg: ByteArray,
        val timestampStartCameraMs: Long,
        val timestampCaptureBeginMs: Long,
        val timestampCaptureEndMs: Long,
        val timestampCompressDoneMs: Long,
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ThumbnailInfo

            if (fileTimestampMs != other.fileTimestampMs) return false
            if (timestampStartCameraMs != other.timestampStartCameraMs) return false
            if (timestampCaptureBeginMs != other.timestampCaptureBeginMs) return false
            if (timestampCaptureEndMs != other.timestampCaptureEndMs) return false
            if (timestampCompressDoneMs != other.timestampCompressDoneMs) return false
            // do not compare jpeg, it's too large.

            return true
        }

        override fun hashCode(): Int {
            var result = fileTimestampMs.hashCode()
            result = 31 * result + timestampStartCameraMs.hashCode()
            result = 31 * result + timestampCaptureBeginMs.hashCode()
            result = 31 * result + timestampCaptureEndMs.hashCode()
            result = 31 * result + timestampCompressDoneMs.hashCode()
            // do not include jpeg, it's too large.
            return result
        }
    }



    interface CaptureImageCallback {
        fun onCaptureBegin() {}
        fun onThumbnailComplete(thumbnail: ThumbnailInfo) {}
        fun onQualityImageComplete(imageFile: File?, thumbFile: File?, fileTimestampMs: Long = 0) {}
    }

    fun saveJpegFile(path: String, name: String, jpeg: ByteArray, size: Int, timestampMs: Long): File {
        val directory = File(path)
        if (!directory.exists()) {
            directory.mkdirs()
        }

        val file = File(directory, "$name.jpg")

        FileOutputStream(file).use { outputStream ->
            outputStream.write(jpeg, 0, size)  // 写入字节数据
            outputStream.flush()
        }

        file.setLastModified(timestampMs)

        return file
    }


    private fun saveJpegImage(image: Image?, timestampMs: Long): File? {
        if (image == null) {
            return null
        }
        if (image.format != ImageFormat.JPEG) {
            return null
        }
        image.use { image ->
            val buffer = image.planes[0].buffer
            val length = buffer.remaining()
            if (length > IMAGE_JPEG_BUF_DEF_LEN) {
                IMAGE_JPEG_BUF = ByteArray(length)
            }
            buffer.get(IMAGE_JPEG_BUF, 0, length)
            val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
            val fileName = "photo_$timeStamp"

            val sdcardDir = Environment.getExternalStorageDirectory()
            val videoRecordDir = File(sdcardDir, "videorecord")
            val path = videoRecordDir?.absolutePath
            return saveJpegFile(
                path.toString(), fileName,
                IMAGE_JPEG_BUF, length, timestampMs)
        }
    }


    private fun saveExif(imageFile: File, timestampMs: Long, exposureTime: Long?, sensitivity: Int?) {
        val valueMake = Build.BRAND
        val valueModel = Build.MODEL
        val valueDateTime = 0L
        val valueExposureTime = exposureTime?.let { (it.toDouble() / 1_000_000_000).toString() }
        val valueIso = sensitivity?.toString()
        val exif = ExifInterface(imageFile)
        exif.setAttribute(ExifInterface.TAG_MAKE, Build.BRAND)
        exif.setAttribute(ExifInterface.TAG_MODEL, Build.MODEL)
        valueExposureTime?.also { exif.setAttribute(ExifInterface.TAG_EXPOSURE_TIME, it) }
        valueIso?.also { @Suppress("DEPRECATION") exif.setAttribute(ExifInterface.TAG_ISO_SPEED_RATINGS, valueIso) }
        exif.saveAttributes()
        Log.d(TAG, "saveExif: ${imageFile.name}, time=$valueDateTime, make=$valueMake, model=$valueModel, exposureTime=$valueExposureTime, iso=$valueIso")
    }


    private class SessionCaptureCallback : CameraCaptureSession.CaptureCallback() {
        private var myOnCaptureResult: (CameraCaptureSession, CaptureRequest, TotalCaptureResult?, Long, CaptureFailure?) -> Unit = { _, _, _, _, _ -> }
        var isFinished = false
        var startTimestampMs: Long = 0
        fun setOnCaptureResult(onCaptureResult: (CameraCaptureSession, CaptureRequest, TotalCaptureResult?, Long, CaptureFailure?) -> Unit) {
            myOnCaptureResult = onCaptureResult
        }
        override fun onCaptureStarted(session: CameraCaptureSession, request: CaptureRequest, timestampNs: Long, frameNumber: Long) {
            if (isFinished) return
            startTimestampMs = System.currentTimeMillis() - SystemClock.elapsedRealtime() + (timestampNs / 1000000) // NS to MS
        }
        override fun onCaptureCompleted(session: CameraCaptureSession, request: CaptureRequest, result: TotalCaptureResult) {
            myOnCaptureResult.invoke(session, request, result, startTimestampMs, null)
        }
        override fun onCaptureFailed(session: CameraCaptureSession, request: CaptureRequest, failure: CaptureFailure) {
            myOnCaptureResult.invoke(session, request, null, 0, failure)
        }
    }

    private fun check3aReady(result: TotalCaptureResult): Boolean {
        val aeState = result[CaptureResult.CONTROL_AE_STATE]
        val awbState = result[CaptureResult.CONTROL_AWB_STATE]
        val afState = result[CaptureResult.CONTROL_AF_STATE]
        val exposureTime = result[CaptureResult.SENSOR_EXPOSURE_TIME]
        val sensitivity = result[CaptureResult.SENSOR_SENSITIVITY]
        myCheck3aCount += 1
        Log.v(TAG, StringBuilder().let {
            it.append("check3aReady[%2d]: ".format(myCheck3aCount))
            it.append("aeState=${aeState}, awbState=${awbState}, afState=${afState}, ")
            it.append("exposureTime=%.3fms, ".format(exposureTime?.nanoseconds?.toDouble(DurationUnit.MILLISECONDS)))
            it.append("sensitivity=${sensitivity}")
            it.toString()
        })
        return aeState == CaptureResult.CONTROL_AE_STATE_CONVERGED
                && awbState == CaptureResult.CONTROL_AWB_STATE_CONVERGED
    }

}







