package com.example.cameratest.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.camera.view.PreviewView.StreamState
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.cameratest.camera.CameraController.Companion.SHORT_EDGE
import com.example.cameratest.viewmodel.CameraViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale


class CameraController(private val viewModel: CameraViewModel) {
    companion object {
        const val TAG = "CameraController"
        const val SHORT_EDGE = 720
    }
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var previewEnable: Boolean = true
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

    fun coldStartAndTakePhoto(context: Context, owner: LifecycleOwner, onImageSaved: (Bitmap, ByteArray) -> Unit) {
        stopCameraPreview()
        bindCamera(owner)
        capturePhoto(context, owner, onImageSaved)
    }

    fun startCameraPreview(owner : LifecycleOwner) {
        bindCamera(owner)
    }

    private fun bindCamera(owner: LifecycleOwner) {
        Log.d(TAG, "bindCamera: ")
        viewModel.setCameraInactiveTime(System.currentTimeMillis())
        previewView?.previewStreamState?.observe(owner, previewStreamStateObserver)
        val previewBuilder = Preview.Builder()
        val preview = previewBuilder.build().also {
            it.setSurfaceProvider(previewView?.surfaceProvider)
        }

        imageCapture = ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()

        val camSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        try {
            camera = cameraProvider?.bindToLifecycle(
                owner,
                camSelector,
                preview,
                imageCapture
            )
            Log.d(TAG, "setCameraReadyTime: ")
            viewModel.setCameraReadyTime(System.currentTimeMillis())
            viewModel.onCameraUsedTimeChanged()
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
    }

    fun capturePhoto(context : Context, owner : LifecycleOwner, onImageSaved : (Bitmap, ByteArray) -> Unit) = owner.lifecycleScope.launch {
        val imageCapture = imageCapture ?: return@launch
        viewModel.setStartTakePhotoTime(System.currentTimeMillis())
        imageCapture.takePicture(ContextCompat.getMainExecutor(context), object :
            ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureSuccess(image: ImageProxy) {
                super.onCaptureSuccess(image)
                viewModel.setImageCapturedTime(System.currentTimeMillis())
                viewModel.onImageCaptured()
                owner.lifecycleScope.launch {
                    //encoded jpeg
                    val bitmap = processImageFromBitmap(imageProxyToBitmap(owner, image),
                        image.imageInfo.rotationDegrees.toFloat())
                    viewModel.setJpegEncodedTime(System.currentTimeMillis())
                    viewModel.onJpegEncoded()
                    //save jpeg to storage
                    val sdf = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault())
                    viewModel.saveMediaToStorage(context, bitmap, sdf.format(Date()))
                    viewModel.setJpegSavedTime(System.currentTimeMillis())
                    viewModel.onJpegSaved()
                    Log.d(TAG, "onJpegSaved: ")
                    viewModel.generateThumbnail(context, bitmap, onImageSaved)
                    val outputStream = ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
                    image.close()
                }
            }

            override fun onError(exception: ImageCaptureException) {
                super.onError(exception)
                Log.d(TAG,"onCaptureSuccess: onError : " + exception.message)
            }
        })
    }

    private suspend fun imageProxyToBitmap(owner : LifecycleOwner, image: ImageProxy): Bitmap =
        withContext(owner.lifecycleScope.coroutineContext) {
            val planeProxy = image.planes[0]
            val buffer: ByteBuffer = planeProxy.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
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