package com.example.cameratest.camera

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.StreamState
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.cameratest.MainActivity
import com.example.cameratest.R
import com.example.cameratest.camera.CameraController.Companion.SHORT_EDGE
import com.example.cameratest.utils.OrientationService
import com.example.cameratest.utils.StorageUtil
import com.example.cameratest.viewmodel.CameraViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale


class CameraController(private val viewModel: CameraViewModel) {
    companion object {
        const val TAG = "CameraController"
        const val SHORT_EDGE = 720
        const val PHOTO = 0
        const val VIDEO = 1
    }

    private var cameraInactiveTime: Long = 0
    private var cameraReadyTime: Long = 0
    private var startTakePhotoTime: Long = 0
    private var imageCaptureStartedTime: Long = 0
    private var imageCapturedTime: Long = 0
    private var jpegEncodedTime: Long = 0
    private var jpegSavedTime: Long = 0
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var previewView: PreviewView? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var previewEnable: Boolean = false
    private val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val cameraMode = arrayOf(PHOTO, VIDEO)
    private var currentCameraMode = cameraMode[0]
    private val previewStreamStateObserver = Observer<StreamState> {
        if (it == StreamState.STREAMING) {
            if (previewEnable) {
                previewView?.visibility = View.VISIBLE
            }
        } else if (it == StreamState.IDLE) {
            if (previewEnable) {
                previewView?.visibility = View.INVISIBLE
            }
        }
    }

    fun getAvailableCamera(context : Context): List<Int> {
        cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val cameraSelectorList = mutableListOf<Int>()
        if (hasBackCamera()) {
            cameraSelectorList.add(CameraSelector.LENS_FACING_BACK)
        }
        if (hasFrontCamera()) {
            cameraSelectorList.add(CameraSelector.LENS_FACING_FRONT)
        }
        return cameraSelectorList
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) ?: false
    }

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(): Boolean {
        return cameraProvider?.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA) ?: false
    }

    fun setLens(lens:Int) {
        lensFacing = lens
    }

    fun setCameraMode(mode:Int) {
        currentCameraMode = mode
    }

    fun getCameraModeList(): List<Int> {
        return cameraMode.asList()
    }

    fun getPreviewView(context: Context): PreviewView {
        if (previewView == null) {
            previewView = PreviewView(context)
        }
        previewView?.visibility = View.INVISIBLE
        return previewView as PreviewView
    }

    fun setPreviewEnable(enable: Boolean) {
        previewEnable = enable
        Log.d(TAG, "setPreviewEnable: $previewEnable")
    }

    fun coldStartAndTakePhoto(context: Context, owner: LifecycleOwner, isOnImageSavedCallback: Boolean, onImageSaved: (Bitmap, ByteArray) -> Unit) {
        stopCameraPreview()
        bindCamera(context, owner)
        capturePhoto(context, owner, isOnImageSavedCallback, true, onImageSaved)
    }

    fun startCameraPreview(context: Context, owner : LifecycleOwner) {
        bindCamera(context, owner)
    }

    private fun bindCamera(context: Context, owner: LifecycleOwner) {
        Log.d(TAG, "bindCamera: ")
//        viewModel.setCameraInactiveTime(System.currentTimeMillis())
        viewModel.updateJpegSavedStatus(false)
        cameraInactiveTime = System.currentTimeMillis()
        previewView?.previewStreamState?.observe(owner, previewStreamStateObserver)
        val previewBuilder = Preview.Builder()
        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(previewView?.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val recorder = Recorder.Builder()
            .setQualitySelector(QualitySelector.from(Quality.HIGHEST))
            .build()
        videoCapture = VideoCapture.withOutput(recorder)

        val camSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        try {
            cameraProvider?.unbindAll()
            camera = cameraProvider?.bindToLifecycle(
                owner,
                camSelector,
                preview,
                if (currentCameraMode == PHOTO) imageCapture else videoCapture
            )

//            viewModel.setCameraReadyTime(System.currentTimeMillis())
//            viewModel.onCameraUsedTimeChanged()
            cameraReadyTime = System.currentTimeMillis()
            val startCameraUsedTime = cameraReadyTime - cameraInactiveTime
            val latencyString =
                context.getString(R.string.latency_from_camera_inactive_to_ready)
            val latencyResult =
                if (startCameraUsedTime > 0) "LATENCY($startCameraUsedTime ms): $latencyString"
                else "LATENCY(-): $latencyString"
            Log.d(TAG, latencyResult)
            viewModel.setCameraActivated(true)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun stopCameraPreview() {
        previewView?.previewStreamState?.removeObserver(previewStreamStateObserver)
        cameraProvider?.unbindAll()
        viewModel.setCameraActivated(false)
        if (previewEnable) {
            previewView?.visibility = View.INVISIBLE
        }
        clearTime()
    }

    fun capturePhoto(context : Context, owner : LifecycleOwner, isOnImageSavedCallback : Boolean, isCold : Boolean, onImageSaved : (Bitmap, ByteArray) -> Unit) = owner.lifecycleScope.launch {
        val imageCapture = imageCapture ?: return@launch
//        viewModel.clearCaptureTime()
//        viewModel.setStartTakePhotoTime(System.currentTimeMillis())
        clearCaptureTime()
        viewModel.updateJpegSavedStatus(false)
        startTakePhotoTime = System.currentTimeMillis()
        if (isOnImageSavedCallback) {
            val name = System.currentTimeMillis().toString() + ".jpg"
            val contentValues = ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, name)
                put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
            }

            val outputOptions = ImageCapture.OutputFileOptions
                .Builder(context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues)
                .build()
            imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(context),
                object : ImageCapture.OnImageSavedCallback {
                    override fun onCaptureStarted() {
                        super.onCaptureStarted()
                        imageCaptureStartedTime = System.currentTimeMillis()
                        printCaptureStartedLog(context)
//                        viewModel.setImageCaptureStartedTime(System.currentTimeMillis())
//                        viewModel.onImageCaptureStarted()
                    }
                    override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                        val time = System.currentTimeMillis()
//                        viewModel.setImageCapturedTime(time)
//                        viewModel.onImageCaptured()
//                        viewModel.setJpegEncodedTime(time)
//                        viewModel.onJpegEncoded()
//                        viewModel.setJpegSavedTime(time)
//                        viewModel.onJpegSaved()
                        viewModel.updateJpegSavedStatus(true)
                        jpegSavedTime = System.currentTimeMillis()
                        printCaptureDoneLog(context, isOnImageSavedCallback, isCold)
                        val bitmap : Bitmap?  = processImageFromOutputFileResults(context.contentResolver, output)
                        viewModel.generateThumbnail(context, bitmap!!, onImageSaved)
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.e(TAG, "Photo capture failed: ${exception.message}", exception)
                    }
                }
            )
        } else {
            imageCapture.takePicture(ContextCompat.getMainExecutor(context), object :
                ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureStarted() {
                    super.onCaptureStarted()
                    imageCaptureStartedTime = System.currentTimeMillis()
                    printCaptureStartedLog(context)
//                    viewModel.setImageCaptureStartedTime(System.currentTimeMillis())
//                    viewModel.onImageCaptureStarted()
                }
                override fun onCaptureSuccess(image: ImageProxy) {
                    super.onCaptureSuccess(image)
                    imageCapturedTime = System.currentTimeMillis()
//                    viewModel.setImageCapturedTime(System.currentTimeMillis())
//                    viewModel.onImageCaptured()
                    owner.lifecycleScope.launch {
                        var rotationDegrees = when ((context as MainActivity).getOrientationService().layoutOrientation) {
                            OrientationService.LayoutOrientation.Portrait -> 90f
                            OrientationService.LayoutOrientation.Landscape -> 0f
                            OrientationService.LayoutOrientation.ReversePortrait -> 270f
                            OrientationService.LayoutOrientation.ReverseLandscape -> 180f
                            else -> 90f
                        }
                        //encoded jpeg
                        val bitmap = processImageFromBitmap(imageProxyToBitmap(owner, image),
                            rotationDegrees)
                        jpegEncodedTime = System.currentTimeMillis()
//                        viewModel.setJpegEncodedTime(System.currentTimeMillis())
//                        viewModel.onJpegEncoded()
                        //save jpeg to storage
                        val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                        viewModel.saveMediaToStorage(context, bitmap, sdf.format(System.currentTimeMillis()))
                        viewModel.updateJpegSavedStatus(true)
                        jpegSavedTime = System.currentTimeMillis()
                        printCaptureDoneLog(context, isOnImageSavedCallback, isCold)
//                        viewModel.setJpegSavedTime(System.currentTimeMillis())
//                        viewModel.onJpegSaved()
                        viewModel.generateThumbnail(context, bitmap, onImageSaved)
                        image.close()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.d(TAG,"onCaptureSuccess: onError : " + exception.message)
                }
            })
        }
    }

    private fun printCaptureStartedLog (context: Context) {
        val imageCaptureStartedUsedTime = imageCaptureStartedTime - startTakePhotoTime
        val latencyString =
            context.getString(R.string.latency_from_take_photo_to_image_capture_started)
        val latencyResult =
            if (imageCaptureStartedUsedTime > 0) "LATENCY($imageCaptureStartedUsedTime ms): $latencyString"
            else "LATENCY(-): $latencyString"
        Log.d(TAG, latencyResult)
    }

    private fun printCaptureDoneLog(context: Context, isOnImageSavedCallback : Boolean, isCold : Boolean) {
        val imageCapturedUsedTime = imageCapturedTime - imageCaptureStartedTime
        val latency1String =
            context.getString(R.string.latency_from_image_capture_started_to_image_captured)
        val latency1Result =
            if (imageCapturedUsedTime > 0 && !isOnImageSavedCallback) "LATENCY($imageCapturedUsedTime ms): $latency1String"
            else "LATENCY(-): $latency1String"
        Log.d(TAG, latency1Result)
        val jpegEncodedUsedTime = jpegEncodedTime - imageCapturedTime
        val latency2String = context.getString(R.string.latency_from_image_in_ram_to_jpeg_encoded)
        val latency2Result = if (jpegEncodedUsedTime > 0 && !isOnImageSavedCallback)
            "LATENCY($jpegEncodedUsedTime ms): $latency2String" else "LATENCY(-): $latency2String"
        Log.d(TAG, latency2Result)
        val jpegSavedUsedTime = jpegSavedTime - jpegEncodedTime
        val latency3String = context.getString(R.string.latency_from_jpeg_encoded_to_jpeg_file_saved)
        val latency3Result = if (jpegSavedUsedTime > 0 && !isOnImageSavedCallback)
            "LATENCY($jpegSavedUsedTime ms): $latency3String" else "LATENCY(-): $latency3String"
        Log.d(TAG, latency3Result)
        val takePhotoUsedTime = jpegSavedTime - startTakePhotoTime
        val latency4String = context.getString(R.string.latency_from_take_photo_to_jpeg_file_saved)
        val latency4Result = if (takePhotoUsedTime > 0)
            "LATENCY($takePhotoUsedTime ms): $latency4String" else "LATENCY(-): $latency4String"
        Log.d(TAG, latency4Result)
        val startToSavedUsedTime = jpegSavedTime - cameraInactiveTime
        val latency5String = context.getString(R.string.latency_from_start_camera_to_jpeg_file_saved)
        val latency5Result = if (isCold && startToSavedUsedTime > 0)
            "LATENCY($startToSavedUsedTime ms): $latency5String" else "LATENCY(-): $latency5String"
        Log.d(TAG, latency5Result)
    }

    private fun clearTime() {
       cameraInactiveTime = 0
       cameraReadyTime = 0
       startTakePhotoTime = 0
       imageCaptureStartedTime = 0
       imageCapturedTime = 0
       jpegEncodedTime = 0
       jpegSavedTime= 0
    }

    private fun clearCaptureTime() {
        startTakePhotoTime = 0
        imageCaptureStartedTime = 0
        imageCapturedTime = 0
        jpegEncodedTime = 0
        jpegSavedTime= 0
    }

    private suspend fun imageProxyToBitmap(owner : LifecycleOwner, image: ImageProxy): Bitmap =
        withContext(owner.lifecycleScope.coroutineContext) {
            val planeProxy = image.planes[0]
            val buffer: ByteBuffer = planeProxy.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }

    fun processImageFromOutputFileResults(
        contentResolver: ContentResolver,
        outputFileResults: ImageCapture.OutputFileResults
    ): Bitmap? {
        val savedUri: Uri = outputFileResults.savedUri ?: return null

        val inputStream: InputStream? = contentResolver.openInputStream(savedUri)
        val bitmap: Bitmap = BitmapFactory.decodeStream(inputStream)
        inputStream?.close()

        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        val shortEdge = 720
        val aspectRatio: Float = originalWidth.toFloat() / originalHeight.toFloat()

        val (targetWidth, targetHeight) = if (originalWidth < originalHeight) {
            Pair(shortEdge, (shortEdge / aspectRatio).toInt())
        } else {
            Pair((shortEdge * aspectRatio).toInt(), shortEdge)
        }

        val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

        return scaledBitmap
    }

    @SuppressLint("MissingPermission")
    fun startRecording(context : Context, useMediaStore : Boolean, onStartSuccess : () -> Unit,
                       onStartFail : () -> Unit, onImageSaved : (Bitmap, ByteArray) -> Unit) {
        val videoFileName = sdf.format(System.currentTimeMillis()) + ".mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI)
            .setContentValues(contentValues)
            .build()

        val fileStoreOutput = FileOutputOptions.Builder(
            File(
                context.externalMediaDirs.firstOrNull(),
                videoFileName
            )
        ).build()

        val pendingRecording: PendingRecording? = if (useMediaStore) {
            videoCapture?.output?.prepareRecording(context, mediaStoreOutput)
        } else {
            videoCapture?.output?.prepareRecording(context, fileStoreOutput)
        }

        currentRecording = pendingRecording?.apply { withAudioEnabled() }
            ?.start(ContextCompat.getMainExecutor(context)) { recordEvent ->
                when(recordEvent) {
                    is VideoRecordEvent.Start -> {
                        onStartSuccess()
                    }
                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val pfd: ParcelFileDescriptor? = context.contentResolver
                                .openFileDescriptor(recordEvent.outputResults.outputUri, "r")
                            val bitmap : Bitmap  = StorageUtil().createVideoThumbnailBitmap(pfd?.fileDescriptor)
                            viewModel.generateThumbnail(context, bitmap, onImageSaved)
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            currentRecording?.close()
                            currentRecording = null
                            Log.e(TAG, "Video capture ends with error: " +
                                    "${recordEvent.error}")
                            onStartFail()
                        }
                    }
                }
            }
        Log.i(TAG, "Recording started")
    }

    fun stopRecording() {
        if (currentRecording != null) {
            currentRecording?.stop()
            currentRecording = null
        }
    }
}

fun processImageFromBitmap(bitmap: Bitmap, rotationDegrees: Float): Bitmap {
    val originalWidth = bitmap.width
    val originalHeight = bitmap.height
    val aspectRatio: Float = originalWidth.toFloat() / originalHeight.toFloat()
    val (targetWidth, targetHeight) = if (originalWidth < originalHeight) {
        Pair(SHORT_EDGE, (SHORT_EDGE / aspectRatio).toInt())
    } else {
        Pair((SHORT_EDGE * aspectRatio).toInt(), SHORT_EDGE)
    }

    val scaledBitmap = Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)

    val matrix = Matrix().apply {
        postRotate(rotationDegrees)
    }
    val rotatedBitmap = Bitmap.createBitmap(
        scaledBitmap,
        0,
        0,
        scaledBitmap.width,
        scaledBitmap.height,
        matrix,
        true
    )

    return rotatedBitmap
}