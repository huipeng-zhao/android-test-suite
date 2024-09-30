package com.example.cameratest.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.util.Log
import android.view.View
import androidx.annotation.OptIn
import androidx.camera.camera2.interop.ExperimentalCamera2Interop
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.example.cameratest.viewmodel.CameraViewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.nio.ByteBuffer

class CameraController(private val viewModel: CameraViewModel) {
    val TAG = "CameraController"
    private var imageCapture: ImageCapture? = null
    private var previewView: PreviewView? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null
    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK
    private var previewEnable: Boolean = true

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

    fun coldStartAndTakePhoto(context: Context, owner: LifecycleOwner, onImageSaved: (Bitmap) -> Unit) {
        stopCameraPreview()
        bindCamera(owner)
        capturePhoto(context, owner, onImageSaved)
    }

    fun startCameraPreview(owner : LifecycleOwner) {
        bindCamera(owner)
    }

    @OptIn(ExperimentalCamera2Interop::class)
    fun bindCamera(owner: LifecycleOwner) {
        Log.d(TAG, "bindCamera: ")
        viewModel.setCameraInactiveTime(System.currentTimeMillis())
        if (previewEnable) {
            previewView?.visibility = View.VISIBLE
        }
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
        cameraProvider?.unbindAll()
        viewModel.setCameraActivated(false)
        if (previewEnable) {
            previewView?.visibility = View.INVISIBLE
        }
    }

    fun capturePhoto(context : Context, owner : LifecycleOwner, onImageSaved : (Bitmap) -> Unit) = owner.lifecycleScope.launch {
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
                    val bitmap = imageProxyToBitmap(owner, image).rotateBitmap(image.imageInfo.rotationDegrees)
                    viewModel.setJpegEncodedTime(System.currentTimeMillis())
                    viewModel.onJpegEncoded()
                    //save jpeg to storage
                    viewModel.saveMediaToStorage(context, bitmap, System.currentTimeMillis().toString())
                    viewModel.setJpegSavedTime(System.currentTimeMillis())
                    viewModel.onJpegSaved()
                    Log.d(TAG, "onJpegSaved: ")
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

    private suspend fun imageProxyToBitmap(owner : LifecycleOwner, image: ImageProxy): Bitmap =
        withContext(owner.lifecycleScope.coroutineContext) {
            val planeProxy = image.planes[0]
            val buffer: ByteBuffer = planeProxy.buffer
            val bytes = ByteArray(buffer.remaining())
            buffer.get(bytes)
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
    }
}

fun Bitmap.rotateBitmap(rotationDegrees: Int): Bitmap {
    val matrix = Matrix().apply {
        postRotate(-rotationDegrees.toFloat())
        postScale(-1f, -1f)
    }

    return Bitmap.createBitmap(this, 0, 0, width, height, matrix, true)
}