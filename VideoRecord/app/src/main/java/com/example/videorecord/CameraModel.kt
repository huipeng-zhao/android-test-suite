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
    private val TAG = CameraModel::class.java.simpleName

    private val myGcsvRecorder = GcsvRecorder(context)

    val VIDEO_SIZE = Size(1920, 1080)
    val VIDEO_FPS = 30
    val VIDEO_BITRATE = 5_000_000
    var myVideoRecorder: MediaRecorder? = null
    val myCameraExecutor = Executors.newSingleThreadExecutor() // run Camera2 callbacks.
    var myCameraManager: CameraManager? = null
    var myCheck3aCount = 0
    private val myIsImageCapturing = MutableStateFlow<Boolean?>(null)
    private val myIsVideoRecording = MutableStateFlow<Boolean?>(null)
    private var myCameraDevice: CameraDevice? = null
    private var myCameraSession: CameraCaptureSession? = null
    private var myCameraId: String? = null
    var mFileName = MutableStateFlow<File?>(null)
    private var myFrameTimeRecorder: BufferedWriter? = null

    init {
        myCameraManager = context.getSystemService<CameraManager>()!!
        myCameraId = getCameraId()
    }

    val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.RECORD_AUDIO,
        Manifest.permission.CAMERA,
        Manifest.permission.WRITE_EXTERNAL_STORAGE,
        Manifest.permission.READ_EXTERNAL_STORAGE,
        Manifest.permission.RECEIVE_BOOT_COMPLETED,
    )
    var missingPermissions = MutableStateFlow<List<String>?>(null)

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

    private var frameIndex: Int = 0
    fun startRecording(){
        if (isBusy()) {
            return
        }
        myIsVideoRecording.value = true
        frameIndex = 0
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
                    if (!success) {
                        return@createSessionConfiguration
                    }
                    myCameraSession = session
                    val recordRequest = camera.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply { addTarget(mediaRecorder.surface) }.build()
                    session.setSingleRepeatingRequest(recordRequest, myCameraExecutor, object : CameraCaptureSession.CaptureCallback() {
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
                            Log.d(TAG, "3a ready")
                            is3aReady = true
                            startTimestampRecord(videoRecordTimestampMs)
                            startGcsvRecord(videoRecordTimestampMs)
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
        Log.d(TAG, "stopRecording, total captured frames (after 3A converged): $frameIndex")
        measureTime {
            try {
                // Actually, the session will be closed automatically when camera closed, we just close it manually.
                myCameraSession?.close()
                myCameraDevice?.close()
                myFrameTimeRecorder?.close()
                myGcsvRecorder.stop()
                myVideoRecorder?.apply {
                    stop()
                    reset()
                    release()
                }
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
                        Log.d(TAG, "openCamera failed: $e")
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
        Log.d(TAG, "close reason: $reason" )
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







