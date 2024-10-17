package com.example.cameratest.viewmodel

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import androidx.camera.view.PreviewView
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.example.cameratest.camera.CameraController
import com.example.cameratest.data.MediaStoreImage
import com.example.cameratest.utils.StorageUtil
import com.example.cameratest.utils.ThumbnailUtil

class CameraViewModel : ViewModel() {

    private val storageUtil = StorageUtil()
    private val thumbnailUtil = ThumbnailUtil()
    private val cameraController = CameraController(this)

    private var _cameraInactiveTime = MutableLiveData<Long>(0)
    private var _cameraReadyTime = MutableLiveData<Long>(0)
    private var _startCameraUsedTime = MutableLiveData<Long>(0)
    var startCameraUsedTime = _startCameraUsedTime

    private var _startTakePhotoTime = MutableLiveData<Long>(0)
    private var _imageCaptureStartedTime = MutableLiveData<Long>(0)
    private var _imageCapturedTime = MutableLiveData<Long>(0)
    private var _imageCapturedUsedTime = MutableLiveData<Long>(0)
    var imageCapturedUsedTime = _imageCapturedUsedTime
    private var _imageCaptureStartedUsedTime = MutableLiveData<Long>(0)
    var imageCaptureStartedUsedTime = _imageCaptureStartedUsedTime

    private var _jpegEncodedTime = MutableLiveData<Long>(0)
    private var _jpegEncodedUsedTime = MutableLiveData<Long>(0)
    var jpegEncodedUsedTime = _jpegEncodedUsedTime

    private var _jpegSavedTime = MutableLiveData<Long>(0)
    private var _jpegSavedUsedTime = MutableLiveData<Long>(0)
    var jpegSavedUsedTime = _jpegSavedUsedTime

    private var _takePhotoUsedTime = MutableLiveData<Long>(0)
    var takePhotoUsedTime = _takePhotoUsedTime

    private var _startToSavedUsedTime = MutableLiveData<Long>(0)
    val startToSavedUsedTime = _startToSavedUsedTime

    private var _isCameraActivated = MutableLiveData<Boolean>(false)
    var isCameraActivated = _isCameraActivated

    var isCameraStateChanged = MutableLiveData<Boolean>(false)
    var isJpegSaved = MutableLiveData<Boolean>(false)

    private val _images = MutableLiveData<List<MediaStoreImage>>()
    val images: LiveData<List<MediaStoreImage>> get() = _images

    private var _imageBitmap = MutableLiveData<Bitmap>(null)
    val imageBitmap: LiveData<Bitmap> get() = _imageBitmap

    private var _currentCameraMode = MutableLiveData<Int>(0)
    var currentCameraMode = _currentCameraMode

    fun updateJpegSavedStatus(isSaved: Boolean) {
        isJpegSaved.value = isSaved
    }

    fun setCameraInactiveTime(time: Long) {
        _cameraInactiveTime.value = time
        isCameraStateChanged.postValue(false)
        isJpegSaved.postValue(false)
    }

    fun setCameraReadyTime(time: Long) {
        _cameraReadyTime.value = time
        isCameraStateChanged.postValue(false)
    }

    fun setStartTakePhotoTime(time: Long) {
        _startTakePhotoTime.value = time
        isJpegSaved.postValue(false)
    }

    fun setImageCaptureStartedTime(time: Long) {
        _imageCaptureStartedTime.value = time
    }

    fun setImageCapturedTime(time: Long) {
        _imageCapturedTime.value = time
    }

    fun setJpegEncodedTime(time: Long) {
        _jpegEncodedTime.value = time
    }

    fun setJpegSavedTime(time: Long) {
        _jpegSavedTime.value = time
        isJpegSaved.postValue(true)
    }

    fun setCameraActivated(isActivated: Boolean) {
        _isCameraActivated.postValue(isActivated)
        isCameraStateChanged.postValue(true)
    }

    fun onCameraUsedTimeChanged() {
        _startCameraUsedTime.value = _cameraReadyTime.value!! - _cameraInactiveTime.value!!
    }

    fun onImageCaptureStarted() {
        _imageCaptureStartedUsedTime.value = _imageCaptureStartedTime.value!! - _startTakePhotoTime.value!!
    }

    fun onImageCaptured() {
        _imageCapturedUsedTime.value = _imageCapturedTime.value!! - _imageCaptureStartedTime.value!!
    }

    fun onJpegEncoded() {
        _jpegEncodedUsedTime.value = _jpegEncodedTime.value!! - _imageCapturedTime.value!!
    }

    fun onJpegSaved() {
        _jpegSavedUsedTime.value = _jpegSavedTime.value!! - _jpegEncodedTime.value!!
        _takePhotoUsedTime.value = _jpegSavedTime.value!! - _startTakePhotoTime.value!!
        _startToSavedUsedTime.value = _jpegSavedTime.value!! - _cameraInactiveTime.value!!
    }

    fun generateThumbnail(context: Context, bitmap: Bitmap, onImageSaved: (Bitmap, ByteArray) -> Unit) {
        thumbnailUtil.generateThumbnail(context, bitmap, onImageSaved)
    }

    suspend fun saveMediaToStorage(context: Context, bitmap: Bitmap, name: String) {
        storageUtil.saveMediaToStorage(context, bitmap, name)
    }

    suspend fun loadImages(context: Context) {
        val imageList = storageUtil.queryImages(context)
        _images.postValue(imageList)
    }

    fun startCameraPreview(context: Context, owner: LifecycleOwner) {
        cameraController.startCameraPreview(context, owner)
    }

    fun stopCameraPreview() {
        cameraController.stopCameraPreview()
        clearTime()
    }

    fun getAvailableCamera(context: Context): List<Int> {
        return cameraController.getAvailableCamera(context)
    }

    fun getCameraModeList(): List<Int> {
        return cameraController.getCameraModeList()
    }

    fun getPreviewView(context: Context): PreviewView {
        return cameraController.getPreviewView(context)
    }

    fun capturePhoto(
        context: Context,
        owner: LifecycleOwner,
        isOnImageSavedCallback: Boolean) {
        cameraController.capturePhoto(context, owner, isOnImageSavedCallback, false) { bitmap, byteArray ->
            _imageBitmap.value = bitmap
        }
    }

    fun coldStartAndTakePhoto(
        context: Context,
        owner: LifecycleOwner,
        isOnImageSavedCallback: Boolean
    ) {
        cameraController.coldStartAndTakePhoto(context, owner, isOnImageSavedCallback) { bitmap, byteArray ->
            _imageBitmap.value = bitmap
        }
    }

    fun setPreviewEnable(enable: Boolean) {
        cameraController.setPreviewEnable(enable)
    }

    fun setLens(lens: Int) {
        cameraController.setLens(lens)
    }

    fun setCameraMode(mode: Int) {
        _currentCameraMode.value = mode
        cameraController.setCameraMode(mode)
    }

    fun startRecording(context : Context, onStartSuccess : () -> Unit, onStartFail : () -> Unit) {
        cameraController.startRecording(context, true, onStartSuccess, onStartFail) {
                bitmap, byteArray -> _imageBitmap.value = bitmap
        }
    }

    fun stopRecording() {
        cameraController.stopRecording()
    }

    private fun clearTime() {
        _cameraInactiveTime.postValue(0)
        _cameraReadyTime.postValue(0)
        _startCameraUsedTime.postValue(0)
        _startTakePhotoTime.postValue(0)
        _imageCapturedTime.postValue(0)
        _imageCapturedUsedTime.postValue(0)
        _jpegEncodedTime.postValue(0)
        _jpegEncodedUsedTime.postValue(0)
        _jpegSavedTime.postValue(0)
        _jpegSavedUsedTime.postValue(0)
        _takePhotoUsedTime.postValue(0)
        _startToSavedUsedTime.postValue(0)
    }

    fun clearCaptureTime() {
        imageCaptureStartedUsedTime.postValue(0)
        imageCapturedUsedTime.postValue(0)
        jpegEncodedUsedTime.postValue(0)
        jpegSavedUsedTime.postValue(0)
        takePhotoUsedTime.postValue(0)
        startToSavedUsedTime.postValue(0)
    }
}