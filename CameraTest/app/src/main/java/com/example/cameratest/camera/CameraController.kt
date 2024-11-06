package com.example.cameratest.camera

import android.annotation.SuppressLint
import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.View
import android.widget.Toast
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.AspectRatio
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
import com.example.cameratest.utils.OrientationUtil
import com.example.cameratest.utils.StorageUtil
import com.example.cameratest.viewmodel.CameraViewModel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.InputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit


class CameraController(private val viewModel: CameraViewModel) {
    companion object {
        const val TAG = "CameraController"
        const val SHORT_EDGE_720 = 720
        const val SHORT_EDGE_1080 = 1080
        const val PHOTO = 0
        const val VIDEO = 1
        const val LENS_FACING = CameraSelector.LENS_FACING_BACK
    }

    private var cameraInactiveTime: Long = 0
    private var cameraReadyTime: Long = 0
    private var startTakePhotoTime: Long = 0
    private var imageCaptureStartedTime: Long = 0
    private var imageCapturedTime: Long = 0
    private var jpegEncodedTime: Long = 0
    private var jpegSavedTime: Long = 0
    private var startTakePhotoTimeList = mutableListOf<Long>()
    private var imageCaptureStartedTimeList = mutableListOf<Long>()
    private var imageCapturedTimeList = mutableListOf<Long>()
    private var jpegEncodedTimeList = mutableListOf<Long>()
    private var jpegSavedTimeList = mutableListOf<Long>()
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var currentRecording: Recording? = null
    private var previewView: PreviewView? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = LENS_FACING
    private var previewEnable: Boolean = false
    private val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
    private val cameraMode = arrayOf(PHOTO, VIDEO)
    private var currentCameraMode = cameraMode[0]
    private var captureCount = 0
    private var startTime: Long = 0L
    private var burstStartTime: Long = 0L
    private val executor: ExecutorService = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())
    private var doBurst: Boolean = false
    private var maxSize: Size? = null
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

    fun getAvailableCamera(context: Context): List<Int> {
        cameraProvider = ProcessCameraProvider.getInstance(context).get()
        val cameraSelectorList = mutableListOf<Int>()
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (cameraId in cameraManager.cameraIdList) {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId)
            val lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING)
            if (lensFacing != null && !cameraSelectorList.contains(lensFacing)) {
                cameraSelectorList.add(lensFacing)
            }
        }
        return cameraSelectorList
    }

    private fun getMaxResolution(context: Context, cameraId: Int): Size? {
        val cameraManager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        return try {
            val characteristics = cameraManager.getCameraCharacteristics(cameraId.toString())
            val map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
            val outputSizes = map?.getOutputSizes(ImageFormat.JPEG)
            if (!outputSizes.isNullOrEmpty()) {
                for (index in outputSizes.indices) {
                    Log.i(TAG, "resolution [$index] : ${outputSizes[index].width}x${outputSizes[index].height}")
                }
            }
            outputSizes?.maxByOrNull { it.width * it.height }
        } catch (e: CameraAccessException) {
            e.printStackTrace()
            null
        }
    }

    fun setLens(lens: Int) {
        lensFacing = lens
    }

    fun setCameraMode(mode: Int) {
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

    fun coldStartAndTakePhoto(
        context: Context,
        owner: LifecycleOwner,
        isOnImageSavedCallback: Boolean,
        onImageSaved: (Bitmap, ByteArray) -> Unit
    ) {
        stopCameraPreview()
        bindCamera(context, owner)
        capturePhoto(context, owner, isOnImageSavedCallback, true, false, onImageSaved)
    }

    fun startCameraPreview(context: Context, owner: LifecycleOwner) {
        bindCamera(context, owner)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    @SuppressLint("RestrictedApi")
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
        maxSize = getMaxResolution(context, if (lensFacing == CameraSelector.LENS_FACING_BACK) 0 else 1)
        Log.i(TAG, "max resolution: $maxSize")
        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .setMaxResolution(maxSize!!)
            .setTargetAspectRatio(getAspectRatio(maxSize!!))
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

    fun capturePhoto(
        context: Context,
        owner: LifecycleOwner,
        isOnImageSavedCallback: Boolean,
        isCold: Boolean,
        isGenerateMultiJpegs: Boolean,
        onImageSaved: (Bitmap, ByteArray) -> Unit
    ) = owner.lifecycleScope.launch {
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
                .Builder(
                    context.contentResolver,
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )
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
                        val bitmap: Bitmap? =
                            processImageFromOutputFileResults(context.contentResolver, output)
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
                    Log.d(TAG, "onCaptureSuccess origin bitmap: size = ${image.width}x${image.height}")
                    imageCapturedTime = System.currentTimeMillis()
//                    viewModel.setImageCapturedTime(System.currentTimeMillis())
//                    viewModel.onImageCaptured()
                    val aspectRatio = image.width.toFloat() / image.height.toFloat()
                    owner.lifecycleScope.launch {
                        //encoded jpeg
                        if (isGenerateMultiJpegs) {
                            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss_S", Locale.getDefault())
                            val currentTime = System.currentTimeMillis()
                            var originBitmap = imageProxyToBitmap(owner, image)

                            val (targetWidthMax, targetHeightMax) =
                                if ((image.width < image.height && maxSize!!.width > maxSize!!.height)
                                    || image.width > image.height && maxSize!!.width < maxSize!!.height) {
                                Pair(maxSize!!.height, maxSize!!.width)
                            } else {
                                Pair(maxSize!!.width, maxSize!!.height)
                            }
                            val bitmap = processImageFromBitmap(
                                originBitmap,
                                getRotationDegrees(context, image),
                                targetWidthMax,
                                targetHeightMax,
                            )
                            //max-100
                            viewModel.saveMultiJpegsToStorage(
                                context,
                                bitmap,
                                sdf.format(currentTime) + "-" + targetHeightMax + "x" + targetWidthMax + "-100",
                                100, false
                            )
                            val (targetWidth_1080, targetHeight_1080) = if (image.width < image.height) {
                                Pair(SHORT_EDGE_1080, (SHORT_EDGE_1080 / aspectRatio).toInt())
                            } else {
                                Pair((SHORT_EDGE_1080 * aspectRatio).toInt(), SHORT_EDGE_1080)
                            }
                            val bitmap1080 = processImageFromBitmap(
                                originBitmap,
                                getRotationDegrees(context, image),
                                targetWidth_1080,
                                targetHeight_1080,
                            )
                            // 1080p quality: 100/95/90/85
                            for (quality in listOf(100, 95, 90, 85)) {
                                if (maxSize!!.width == 1920 && maxSize!!.height == 1080 && quality == 100) {
                                    continue
                                }
                                viewModel.saveMultiJpegsToStorage(
                                    context,
                                    bitmap1080,
                                    sdf.format(currentTime) + "-${targetHeight_1080}x${targetWidth_1080}-${quality}",
                                    quality,false
                                )
                            }
                            val (targetWidth_720, targetHeight_720) = if (image.width < image.height) {
                                Pair(SHORT_EDGE_720, (SHORT_EDGE_720 / aspectRatio).toInt())
                            } else {
                                Pair((SHORT_EDGE_720 * aspectRatio).toInt(), SHORT_EDGE_720)
                            }
                            val bitmap720 = processImageFromBitmap(
                                originBitmap,
                                getRotationDegrees(context, image),
                                targetWidth_720,
                                targetHeight_720,
                            )
                            // 720p quality: 100/95/90/80
                            for (quality in listOf(100, 95, 90, 80)) {
                                viewModel.saveMultiJpegsToStorage(
                                    context,
                                    bitmap720,
                                    sdf.format(currentTime) + "-${targetHeight_720}x${targetWidth_720}-${quality}",
                                    quality, false
                                )
                            }
                            //MediaStore.Images.Thumbnails.MINI_KIND
                            if (image.height * 4 != image.width * 3) {
                                if (image.width < image.height) {
                                    originBitmap =
                                        cropBitmap(originBitmap, image.width, image.width / 3 * 4)
                                } else {
                                    originBitmap =
                                        cropBitmap(originBitmap, image.height / 3 * 4, image.height)
                                }
                            }
                            val (targetWidthThumb, targetHeightThumb) = if (image.width < image.height) {
                                Pair(384, 512)
                            } else {
                                Pair(512, 384)
                            }
                           val bitmapThumb = processImageFromBitmap(
                                originBitmap,
                                getRotationDegrees(context, image),
                               targetWidthThumb,
                               targetHeightThumb,
                            )
                            viewModel.saveMultiJpegsToStorage(
                                context,
                                bitmapThumb,
                                sdf.format(currentTime) + "-thumbnail",
                                100, true
                            )
                            viewModel.updateJpegSavedStatus(true)
                            viewModel.generateThumbnail(context, bitmap, onImageSaved)
                            image.close()
                        } else {
                            val bitmap = processImageFromBitmap(
                                imageProxyToBitmap(owner, image),
                                getRotationDegrees(context, image)
                            )
                            jpegEncodedTime = System.currentTimeMillis()
//                        viewModel.setJpegEncodedTime(System.currentTimeMillis())
//                        viewModel.onJpegEncoded()
                            //save jpeg to storage
                            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            viewModel.saveMediaToStorage(
                                context,
                                bitmap,
                                sdf.format(System.currentTimeMillis()),
                                false
                            )
                            viewModel.updateJpegSavedStatus(true)
                            jpegSavedTime = System.currentTimeMillis()
                            printCaptureDoneLog(context, isOnImageSavedCallback, isCold)
//                        viewModel.setJpegSavedTime(System.currentTimeMillis())
//                        viewModel.onJpegSaved()
                            viewModel.generateThumbnail(context, bitmap, onImageSaved)
                            image.close()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.d(TAG, "onCaptureSuccess: onError : " + exception.message)
                }
            })
        }
    }

    private fun printCaptureStartedLog(context: Context) {
        val imageCaptureStartedUsedTime = imageCaptureStartedTime - startTakePhotoTime
        val latencyString =
            context.getString(R.string.latency_from_take_photo_to_image_capture_started)
        val latencyResult =
            if (imageCaptureStartedUsedTime > 0)
                "LATENCY($imageCaptureStartedUsedTime ms): $latencyString"
            else
                "LATENCY(-): $latencyString"
        Log.d(TAG, latencyResult)
    }

    private fun printCaptureDoneLog(
        context: Context,
        isOnImageSavedCallback: Boolean,
        isCold: Boolean
    ) {
        val imageCapturedUsedTime = imageCapturedTime - imageCaptureStartedTime
        val latency1String =
            context.getString(R.string.latency_from_image_capture_started_to_image_captured)
        val latency1Result =
            if (imageCapturedUsedTime > 0 && !isOnImageSavedCallback)
                "LATENCY($imageCapturedUsedTime ms): $latency1String"
            else
                "LATENCY(-): $latency1String"
        Log.d(TAG, latency1Result)
        val jpegEncodedUsedTime = jpegEncodedTime - imageCapturedTime
        val latency2String = context.getString(R.string.latency_from_image_in_ram_to_jpeg_encoded)
        val latency2Result = if (jpegEncodedUsedTime > 0 && !isOnImageSavedCallback)
            "LATENCY($jpegEncodedUsedTime ms): $latency2String" else "LATENCY(-): $latency2String"
        Log.d(TAG, latency2Result)
        val jpegSavedUsedTime = jpegSavedTime - jpegEncodedTime
        val latency3String =
            context.getString(R.string.latency_from_jpeg_encoded_to_jpeg_file_saved)
        val latency3Result = if (jpegSavedUsedTime > 0 && !isOnImageSavedCallback)
            "LATENCY($jpegSavedUsedTime ms): $latency3String" else "LATENCY(-): $latency3String"
        Log.d(TAG, latency3Result)
        val takePhotoUsedTime = jpegSavedTime - startTakePhotoTime
        val latency4String = context.getString(R.string.latency_from_take_photo_to_jpeg_file_saved)
        val latency4Result = if (takePhotoUsedTime > 0)
            "LATENCY($takePhotoUsedTime ms): $latency4String" else "LATENCY(-): $latency4String"
        Log.d(TAG, latency4Result)
        val startToSavedUsedTime = jpegSavedTime - cameraInactiveTime
        val latency5String =
            context.getString(R.string.latency_from_start_camera_to_jpeg_file_saved)
        val latency5Result = if (isCold && startToSavedUsedTime > 0)
            "LATENCY($startToSavedUsedTime ms): $latency5String" else "LATENCY(-): $latency5String"
        Log.d(TAG, latency5Result)
    }

    private fun printLogForBurst(context: Context, isOnImageSavedCallback: Boolean) {
        var startTakePhotoTime = 0L
        var imageCaptureStartedTime = 0L
        var imageCapturedTime = 0L
        var jpegEncodedTime = 0L
        var jpegSavedTime = 0L
        if (startTakePhotoTimeList.size > 0) {
            startTakePhotoTime = startTakePhotoTimeList.removeAt(0)
        }
        if (imageCaptureStartedTimeList.size > 0) {
            imageCaptureStartedTime = imageCaptureStartedTimeList.removeAt(0)
        }
        if (imageCapturedTimeList.size > 0) {
            imageCapturedTime = imageCapturedTimeList.removeAt(0)
        }
        if (jpegEncodedTimeList.size > 0) {
            jpegEncodedTime = jpegEncodedTimeList.removeAt(0)
        }
        if (jpegSavedTimeList.size > 0) {
            jpegSavedTime = jpegSavedTimeList.removeAt(0)
        }

        val imageCaptureStartedUsedTime = imageCaptureStartedTime - startTakePhotoTime
        val latencyString =
            context.getString(R.string.latency_from_take_photo_to_image_capture_started)
        val latencyResult =
            if (imageCaptureStartedUsedTime > 0)
                "LATENCY($imageCaptureStartedUsedTime ms): $latencyString"
            else
                "LATENCY(-): $latencyString"
        Log.d(TAG, latencyResult)
        val imageCapturedUsedTime = imageCapturedTime - imageCaptureStartedTime
        val latency1String =
            context.getString(R.string.latency_from_image_capture_started_to_image_captured)
        val latency1Result =
            if (imageCapturedUsedTime > 0 && !isOnImageSavedCallback)
                "LATENCY($imageCapturedUsedTime ms): $latency1String"
            else
                "LATENCY(-): $latency1String"
        Log.d(TAG, latency1Result)
        val jpegEncodedUsedTime = jpegEncodedTime - imageCapturedTime
        val latency2String = context.getString(R.string.latency_from_image_in_ram_to_jpeg_encoded)
        val latency2Result = if (jpegEncodedUsedTime > 0 && !isOnImageSavedCallback)
            "LATENCY($jpegEncodedUsedTime ms): $latency2String" else "LATENCY(-): $latency2String"
        Log.d(TAG, latency2Result)
        val jpegSavedUsedTime = jpegSavedTime - jpegEncodedTime
        val latency3String =
            context.getString(R.string.latency_from_jpeg_encoded_to_jpeg_file_saved)
        val latency3Result = if (jpegSavedUsedTime > 0 && !isOnImageSavedCallback)
            "LATENCY($jpegSavedUsedTime ms): $latency3String" else "LATENCY(-): $latency3String"
        Log.d(TAG, latency3Result)
        val takePhotoUsedTime = jpegSavedTime - startTakePhotoTime
        val latency4String = context.getString(R.string.latency_from_take_photo_to_jpeg_file_saved)
        val latency4Result = if (takePhotoUsedTime > 0)
            "LATENCY($takePhotoUsedTime ms): $latency4String" else "LATENCY(-): $latency4String"
        Log.d(TAG, latency4Result)
        Log.d(TAG, "LATENCY---------------------------------------------------------")
    }

    private fun getAspectRatio(size: Size): Int {
        val width = size.width
        val height = size.height
        return when {
            width * 3 == height * 4 -> AspectRatio.RATIO_4_3
            width * 9 == height * 16 -> AspectRatio.RATIO_16_9
            else -> AspectRatio.RATIO_DEFAULT
        }
    }

    private fun clearTime() {
        cameraInactiveTime = 0
        cameraReadyTime = 0
        startTakePhotoTime = 0
        imageCaptureStartedTime = 0
        imageCapturedTime = 0
        jpegEncodedTime = 0
        jpegSavedTime = 0
    }

    private fun clearCaptureTime() {
        startTakePhotoTime = 0
        imageCaptureStartedTime = 0
        imageCapturedTime = 0
        jpegEncodedTime = 0
        jpegSavedTime = 0
    }

    private fun clearList() {
        startTakePhotoTimeList.clear()
        imageCaptureStartedTimeList.clear()
        imageCapturedTimeList.clear()
        jpegEncodedTimeList.clear()
        jpegSavedTimeList.clear()
    }

    private suspend fun imageProxyToBitmap(owner: LifecycleOwner, image: ImageProxy): Bitmap =
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
    fun startRecording(
        context: Context, useMediaStore: Boolean, onStartSuccess: () -> Unit,
        onStartFail: () -> Unit, onImageSaved: (Bitmap, ByteArray) -> Unit
    ) {
        val videoFileName = sdf.format(System.currentTimeMillis()) + ".mp4"

        val contentValues = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, videoFileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "video/mp4")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
        }
        val mediaStoreOutput = MediaStoreOutputOptions.Builder(
            context.contentResolver,
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        )
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
                when (recordEvent) {
                    is VideoRecordEvent.Start -> {
                        onStartSuccess()
                    }

                    is VideoRecordEvent.Finalize -> {
                        if (!recordEvent.hasError()) {
                            val pfd: ParcelFileDescriptor? = context.contentResolver
                                .openFileDescriptor(recordEvent.outputResults.outputUri, "r")
                            val bitmap: Bitmap =
                                StorageUtil().createVideoThumbnailBitmap(pfd?.fileDescriptor)
                            viewModel.generateThumbnail(context, bitmap, onImageSaved)
                            val msg = "Video capture succeeded: " +
                                    "${recordEvent.outputResults.outputUri}"
                            Toast.makeText(context, msg, Toast.LENGTH_SHORT)
                                .show()
                            Log.d(TAG, msg)
                        } else {
                            currentRecording?.close()
                            currentRecording = null
                            Log.e(
                                TAG, "Video capture ends with error: " +
                                        "${recordEvent.error}"
                            )
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

    fun startBurstCapture(
        context: Context,
        owner: LifecycleOwner,
        isOnImageSavedCallback: Boolean,
        isBurstFinish: (burstCount: Int, isManualStop: Boolean) -> Unit,
        onImageSaved: (Bitmap, ByteArray) -> Unit
    ) {
        captureCount = 0
        burstStartTime = System.currentTimeMillis()
        doBurst = true

        handler.post {
            takePictureBurst(context, owner, isOnImageSavedCallback, isBurstFinish, onImageSaved)
        }
    }

    fun stopBurstCapture() {
        doBurst = false
    }

    private fun takePictureBurst(
        context: Context,
        owner: LifecycleOwner,
        isOnImageSavedCallback: Boolean,
        isBurstFinish: (burstCount: Int, isManualStop: Boolean) -> Unit,
        onImageSaved: (Bitmap, ByteArray) -> Unit
    ) {
        startTime = System.currentTimeMillis()
        startTakePhotoTime = System.currentTimeMillis()
        startTakePhotoTimeList.add(startTakePhotoTime)
        if (isOnImageSavedCallback) {
            imageCapture?.takePicture(
                makeOutputOptionsForPhoto(context),
                executor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onCaptureStarted() {
                        super.onCaptureStarted()
                        imageCaptureStartedTime = System.currentTimeMillis()
                        imageCaptureStartedTimeList.add(imageCaptureStartedTime)
                    }

                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        jpegSavedTime = System.currentTimeMillis()
                        jpegSavedTimeList.add(jpegSavedTime)
                        captureCount++
                        val endTime = System.currentTimeMillis()
                        val timeTaken = endTime - startTime
                        Log.d(TAG, "timeTaken: $timeTaken")
                        if (System.currentTimeMillis() - burstStartTime < TimeUnit.SECONDS.toMillis(
                                10
                            )
                            && doBurst
                        ) {
                            handler.post {
                                takePictureBurst(
                                    context,
                                    owner,
                                    isOnImageSavedCallback,
                                    isBurstFinish,
                                    onImageSaved
                                )
                            }
                        } else {
                            isBurstFinish(captureCount, !doBurst)
                            doBurst = false
                            Log.d(TAG, "In ten seconds, $captureCount photos were captured.")
                        }
                        printLogForBurst(context, isOnImageSavedCallback)
                        if (!doBurst) {
                            clearList()
                        }
                        owner.lifecycleScope.launch {
                            val bitmap: Bitmap? = processImageFromOutputFileResults(
                                context.contentResolver,
                                outputFileResults
                            )
                            viewModel.generateThumbnail(context, bitmap!!, onImageSaved)
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        Log.d(TAG, "Photo capture failed: ${exception.message}")
                        isBurstFinish(captureCount, !doBurst)
                        doBurst = false
                    }
                })
        } else {
            imageCapture?.takePicture(ContextCompat.getMainExecutor(context), object :
                ImageCapture.OnImageCapturedCallback() {
                override fun onCaptureStarted() {
                    super.onCaptureStarted()
                    imageCaptureStartedTime = System.currentTimeMillis()
                    imageCaptureStartedTimeList.add(imageCaptureStartedTime)
                }

                override fun onCaptureSuccess(image: ImageProxy) {
                    CoroutineScope(Dispatchers.IO).launch {
                        imageCapturedTime = System.currentTimeMillis()
                        imageCapturedTimeList.add(imageCapturedTime)
                        captureCount++
                        val endTime = System.currentTimeMillis()
                        val timeTaken = endTime - startTime
                        Log.d(TAG, "timeTaken: $timeTaken")
                        if (System.currentTimeMillis() - burstStartTime < TimeUnit.SECONDS.toMillis(
                                10
                            )
                            && doBurst
                        ) {
                            handler.post {
                                takePictureBurst(
                                    context,
                                    owner,
                                    isOnImageSavedCallback,
                                    isBurstFinish,
                                    onImageSaved
                                )
                            }
                        } else {
                            isBurstFinish(captureCount, !doBurst)
                            doBurst = false
                            Log.d(TAG, "In ten seconds, $captureCount photos were captured.")
                        }
                        owner.lifecycleScope.launch {
                            //encoded jpeg
                            val bitmap = processImageFromBitmap(
                                imageProxyToBitmap(owner, image),
                                getRotationDegrees(context, image)
                            )
                            jpegEncodedTime = System.currentTimeMillis()
                            jpegEncodedTimeList.add(jpegEncodedTime)
                            //save jpeg to storage
                            val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                            viewModel.saveMediaToStorage(
                                context,
                                bitmap,
                                sdf.format(System.currentTimeMillis()),
                                true
                            )
                            jpegSavedTime = System.currentTimeMillis()
                            jpegSavedTimeList.add(jpegSavedTime)
                            printLogForBurst(context, isOnImageSavedCallback)
                            if (!doBurst) {
                                clearList()
                            }
                            viewModel.generateThumbnail(context, bitmap, onImageSaved)
                            image.close()
                        }
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    super.onError(exception)
                    Log.d(TAG, "onCaptureSuccess: onError : " + exception.message)
                    isBurstFinish(captureCount, !doBurst)
                    doBurst = false
                }
            })
        }

    }

    private fun makeOutputOptionsForPhoto(context: Context): ImageCapture.OutputFileOptions {
        return ImageCapture.OutputFileOptions
            .Builder(context.contentResolver,
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                ContentValues().apply {
                    put(
                        MediaStore.MediaColumns.DISPLAY_NAME,
                        System.currentTimeMillis().toString() + ".jpg"
                    )
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DCIM)
                }).build()
    }

    private fun getRotationDegrees(context: Context, image: ImageProxy) : Float{
        val rotationDegrees: Float =
            when ((context as MainActivity).getOrientationUtil().layoutOrientation) {
                OrientationUtil.LayoutOrientation.Portrait ->
                    image.imageInfo.rotationDegrees.toFloat()

                OrientationUtil.LayoutOrientation.Landscape ->
                    if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        image.imageInfo.rotationDegrees + 270f
                    else
                        image.imageInfo.rotationDegrees + 90f

                OrientationUtil.LayoutOrientation.ReversePortrait ->
                    image.imageInfo.rotationDegrees + 180f

                OrientationUtil.LayoutOrientation.ReverseLandscape ->
                    if (lensFacing == CameraSelector.LENS_FACING_BACK)
                        image.imageInfo.rotationDegrees + 90f
                    else
                        image.imageInfo.rotationDegrees + 270f

                else ->
                    image.imageInfo.rotationDegrees.toFloat()
            }
        return rotationDegrees
    }

    fun processImageFromBitmap(bitmap: Bitmap, rotationDegrees: Float): Bitmap {
        Log.d(TAG, "origin bitmap: size = ${bitmap.width}x${bitmap.height}")
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height
        val aspectRatio: Float = originalWidth.toFloat() / originalHeight.toFloat()
        val (targetWidth, targetHeight) = if (originalWidth < originalHeight) {
            Pair(SHORT_EDGE_720, (SHORT_EDGE_720 / aspectRatio).toInt())
        } else {
            Pair((SHORT_EDGE_720 * aspectRatio).toInt(), SHORT_EDGE_720)
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

    fun processImageFromBitmap(bitmap: Bitmap, rotationDegrees: Float, targetWidth: Int, targetHeight: Int): Bitmap {
        Log.d(TAG, "origin bitmap: size = ${bitmap.width}x${bitmap.height}")
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

    fun cropBitmap(bitmap: Bitmap, targetWidth: Int, targetHeight: Int): Bitmap {
        val originalWidth = bitmap.width
        val originalHeight = bitmap.height

        val x = (originalWidth - targetWidth) / 2
        val y = (originalHeight - targetHeight) / 2

        return Bitmap.createBitmap(bitmap, x, y, targetWidth, targetHeight)
    }
}