package com.example.cameratest.ui.compose

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.example.cameratest.R
import com.example.cameratest.camera.CameraController
import com.example.cameratest.utils.StorageUtil
import com.example.cameratest.viewmodel.CameraViewModel
import kotlinx.coroutines.launch

@Composable
fun CameraPreview(
    context: Context,
    owner: LifecycleOwner,
    viewModel: CameraViewModel,
    onFinish: () -> Unit,
    navigateToGallery: () -> Unit
) {
    val storageUtil = StorageUtil()
    val coroutineScope = rememberCoroutineScope()
    val requiredPermission = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )
    var hasCamPermission by remember {
        mutableStateOf(
            requiredPermission.all {
                ContextCompat.checkSelfPermission(context, it) ==
                        PackageManager.PERMISSION_GRANTED
            })
    }
    var checked by rememberSaveable { mutableStateOf(false) }

    var isCameraButtonEnabled by remember { mutableStateOf(true) }
    var isCaptureButtonEnabled by remember { mutableStateOf(true) }

    var lensSelectedOption by remember { mutableIntStateOf(0) }
    var isTakePhotoCold by remember { mutableStateOf(false) }
    var isRecording by remember { mutableStateOf(false) }
    var isOnImageSavedCallback by remember { mutableStateOf(false) }

    val lensOptions = viewModel.getAvailableCamera(context)
    val modeOptions = viewModel.getCameraModeList()

    val startCameraUsedTime by viewModel.startCameraUsedTime.observeAsState()
    val imageCaptureStartedUsedTime by viewModel.imageCaptureStartedUsedTime.observeAsState()
    val imageCapturedUsedTime by viewModel.imageCapturedUsedTime.observeAsState()
    val jpegEncodedUsedTime by viewModel.jpegEncodedUsedTime.observeAsState()
    val jpegSavedUsedTime by viewModel.jpegSavedUsedTime.observeAsState()
    val takePhotoUsedTime by viewModel.takePhotoUsedTime.observeAsState()
    val startToSavedUsedTime by viewModel.startToSavedUsedTime.observeAsState()
    val activated by viewModel.isCameraActivated.observeAsState()
    val isCameraStateChanged by viewModel.isCameraStateChanged.observeAsState()
    val isJpegSaved by viewModel.isJpegSaved.observeAsState()
    val imageBitmap by viewModel.imageBitmap.observeAsState()
    val currentCameraMode by viewModel.currentCameraMode.observeAsState()

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { granted ->
            hasCamPermission = granted.all {
                it.value
            }

            if (!hasCamPermission) {
                onFinish.invoke()
            }
        }
    )

    LaunchedEffect(key1 = true) {
        launcher.launch(
            requiredPermission
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        if (hasCamPermission) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = {
                    viewModel.getPreviewView(context)
                }
            )

        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(10.dp)
    ) {
//        Column(Modifier.fillMaxWidth()) {
//            Spacer(
//                modifier = Modifier
//                    .statusBarsPadding()
//                    .fillMaxWidth()
//            )
//
//            if (activated == true && currentCameraMode == CameraController.PHOTO) {
//                var latencyText = stringResource(R.string.latency_from_camera_inactive_to_ready)
//                var latencyResult =
//                    if (startCameraUsedTime!! > 0) "$latencyText $startCameraUsedTime ms"
//                    else "$latencyText -"
//                Text(
//                    text = latencyResult,
//                    color = Color(0xffffffff),
//                    fontSize = 14.sp,
//                    modifier = Modifier.align(Alignment.Start)
//                )
//                latencyText = stringResource(R.string.latency_from_take_photo_to_image_capture_started)
//                latencyResult =
//                    if (imageCaptureStartedUsedTime!! > 0) "$latencyText $imageCaptureStartedUsedTime ms"
//                    else "$latencyText -"
//                Text(
//                    text = latencyResult,
//                    color = Color(0xffffffff),
//                    fontSize = 14.sp,
//                    modifier = Modifier.align(Alignment.Start)
//                )
//                latencyText = stringResource(R.string.latency_from_take_photo_to_image_captured)
//                latencyResult =
//                    if (imageCapturedUsedTime!! > 0 && !isOnImageSavedCallback) "$latencyText $imageCapturedUsedTime ms"
//                    else "$latencyText -"
//                Text(
//                    text = latencyResult,
//                    color = Color(0xffffffff),
//                    fontSize = 14.sp,
//                    modifier = Modifier.align(Alignment.Start)
//                )
//                latencyText = stringResource(R.string.latency_from_image_in_ram_to_jpeg_encoded)
//                latencyResult =
//                    if (jpegEncodedUsedTime!! > 0) "$latencyText $jpegEncodedUsedTime ms"
//                    else "$latencyText -"
//                Text(
//                    text = latencyResult,
//                    color = Color(0xffffffff),
//                    fontSize = 14.sp,
//                    modifier = Modifier.align(Alignment.Start)
//                )
//                latencyText = stringResource(R.string.latency_from_jpeg_encoded_to_jpeg_file_saved)
//                latencyResult =
//                    if (jpegSavedUsedTime!! > 0) "$latencyText $jpegSavedUsedTime ms"
//                    else "$latencyText -"
//                Text(
//                    text = latencyResult,
//                    color = Color(0xffffffff),
//                    fontSize = 14.sp,
//                    modifier = Modifier.align(Alignment.Start)
//                )
//                latencyText = stringResource(R.string.latency_from_take_photo_to_jpeg_file_saved)
//                latencyResult =
//                    if (takePhotoUsedTime!! > 0) "$latencyText $takePhotoUsedTime ms"
//                    else "$latencyText -"
//                Text(
//                    text = latencyResult,
//                    color = Color(0xffffffff),
//                    fontSize = 14.sp,
//                    modifier = Modifier.align(Alignment.Start)
//                )
//                latencyText = stringResource(R.string.latency_from_start_camera_to_jpeg_file_saved)
//                latencyResult =
//                    if (isTakePhotoCold && startToSavedUsedTime!! > 0)
//                        "$latencyText $startToSavedUsedTime ms"
//                    else "$latencyText -"
//                Text(
//                    text = latencyResult,
//                    color = Color(0xffffffff),
//                    fontSize = 14.sp,
//                    modifier = Modifier.align(Alignment.Start)
//                )
//            }
//        }

        Column(
            verticalArrangement = Arrangement.spacedBy((-10).dp, Alignment.Top),
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .statusBarsPadding()
                .align(Alignment.BottomCenter)
        ) {
            // thumbnail layout
            if (activated == true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(),
                ) {
                    imageBitmap?.let { bitmap ->
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier
                                .size(100.dp)
                                .padding(16.dp)
                                .align(Alignment.BottomEnd)
                                .clickable { navigateToGallery() }
                        )
                    }
                }
            }

            // select mode layout
            if (activated != true) {
                Row {
                    modeOptions.forEachIndexed { index, option ->
                        RadioButton(selected = (currentCameraMode == index),
                            modifier = Modifier.scale(0.5f),
                            onClick = {
                                viewModel.setCameraMode(option)
                            }
                        )
                        Text(
                            text = when (option) {
                                CameraController.PHOTO ->
                                    stringResource(R.string.camera_mode_photo_text)

                                CameraController.VIDEO ->
                                    stringResource(R.string.camera_mode_video_text)

                                else -> TODO()
                            },
                            color = Color(0xffffffff),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .clickable(onClick = {
                                    viewModel.setCameraMode(option)
                                })
                        )
                    }
                }
            }

            // select lens layout
            if (activated != true) {
                Row {
                    lensOptions.forEachIndexed { index, option ->
                        RadioButton(selected = (lensSelectedOption == index),
                            modifier = Modifier.scale(0.5f),
                            onClick = {
                                lensSelectedOption = index
                                viewModel.setLens(option)
                            }
                        )
                        Text(
                            text = when (option) {
                                CameraSelector.LENS_FACING_BACK ->
                                    stringResource(R.string.radio_button_back_camera)

                                CameraSelector.LENS_FACING_FRONT ->
                                    stringResource(R.string.radio_button_front_camera)

                                else -> TODO()
                            },
                            color = Color(0xffffffff),
                            fontSize = 10.sp,
                            modifier = Modifier
                                .align(Alignment.CenterVertically)
                                .clickable(onClick = {
                                    lensSelectedOption = index
                                    viewModel.setLens(option)
                                })
                        )
                    }
                }
            }

            // enable preview layout
            if (activated != true) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                ) {
                    Text(
                        text = stringResource(R.string.switch_enable_preview),
                        color = Color(0xffffffff),
                        fontSize = 10.sp,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(20.dp, 0.dp, 0.dp, 0.dp)
                    )
                    Switch(checked = checked, onCheckedChange = {
                        checked = it
                        viewModel.setPreviewEnable(it)
                    },
                        Modifier
                            .align(Alignment.CenterEnd)
                            .scale(0.5f))
                }
            }

            // start camera button and check storage button
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                OutlinedButton(
                    enabled = isCameraButtonEnabled,
                    modifier = Modifier
                        .align(Alignment.CenterStart)
                        .scale(0.5f),
                    onClick = {
                        isCameraButtonEnabled = !isCameraButtonEnabled
                        if (activated == true) {
                            viewModel.stopCameraPreview()
                            viewModel.stopRecording()
                        } else {
                            viewModel.startCameraPreview(context, owner)
                        }
                    }
                ) {
                    Text(
                        text = if (activated == true) stringResource(R.string.button_stop_camera)
                        else stringResource(R.string.button_start_camera),
                        fontSize = 14.sp,
                        color = if (isCameraButtonEnabled) Color.White else Color.Gray
                    )
                    if (isCameraStateChanged == true) {
                        isCameraButtonEnabled = true
                    }
                }

                if (activated != true) {
                    OutlinedButton(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .scale(0.5f),
                        onClick = {
                            val (available, percent) = storageUtil.isStorageAvailable()
                            if (available) {
                                Toast
                                    .makeText(
                                        context,
                                        "Storage is available, remain $percent%",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            } else {
                                coroutineScope.launch {
                                    val images = storageUtil.getOldestImages(context)
                                    images.forEach { image ->
                                        storageUtil.performDeleteImage(
                                            context,
                                            image.first,
                                            image.second
                                        )
                                    }
                                }
                                Toast
                                    .makeText(
                                        context,
                                        "Storage is not available, remain $percent%, will delete some files",
                                        Toast.LENGTH_SHORT
                                    )
                                    .show()
                            }
                        }
                    ) {
                        Text(
                            text = stringResource(R.string.button_check_storage),
                            fontSize = 14.sp,
                            color = Color.White
                        )

                    }
                }
            }

            //capture button
            Box(
                modifier = Modifier
                    .fillMaxWidth(),
            ) {
                if (currentCameraMode == CameraController.PHOTO) {
                    if (activated == true) {
                        OutlinedButton(
                            enabled = isCaptureButtonEnabled,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .scale(0.5f),
                            onClick = {
                                val (available, percent) = storageUtil.isStorageAvailable()
                                if (available) {
                                    isTakePhotoCold = false
                                    isOnImageSavedCallback = false
                                    isCaptureButtonEnabled = !isCaptureButtonEnabled
                                    viewModel.capturePhoto(context, owner, false)
                                } else {
                                    coroutineScope.launch {
                                        val images = storageUtil.getOldestImages(context)
                                        images.forEach { image ->
                                            storageUtil.performDeleteImage(
                                                context,
                                                image.first,
                                                image.second
                                            )
                                        }
                                    }
                                    Toast
                                        .makeText(
                                            context,
                                            "Storage is not available, remain $percent%, will delete some files",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.button_take_photo_warm1),
                                fontSize = 14.sp,
                                color = if (isCaptureButtonEnabled) Color.White else Color.Gray
                            )
                            if (isJpegSaved == true) {
                                isCaptureButtonEnabled = true
                            }
                        }

                        OutlinedButton(
                            enabled = isCaptureButtonEnabled,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .scale(0.5f),
                            onClick = {
                                val (available, percent) = storageUtil.isStorageAvailable()
                                if (available) {
                                    isTakePhotoCold = false
                                    isOnImageSavedCallback = true
                                    isCaptureButtonEnabled = !isCaptureButtonEnabled
                                    viewModel.capturePhoto(context, owner, true)
                                } else {
                                    coroutineScope.launch {
                                        val images = storageUtil.getOldestImages(context)
                                        images.forEach { image ->
                                            storageUtil.performDeleteImage(
                                                context,
                                                image.first,
                                                image.second
                                            )
                                        }
                                    }
                                    Toast
                                        .makeText(
                                            context,
                                            "Storage is not available, remain $percent%, will delete some files",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.button_take_photo_warm2),
                                fontSize = 14.sp,
                                color = if (isCaptureButtonEnabled) Color.White else Color.Gray
                            )
                            if (isJpegSaved == true) {
                                isCaptureButtonEnabled = true
                            }
                        }
                    } else {
                        OutlinedButton(
                            enabled = isCaptureButtonEnabled,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .scale(0.5f),
                            onClick = {
                                val (available, percent) = storageUtil.isStorageAvailable()
                                if (available) {
                                    isTakePhotoCold = true
                                    isOnImageSavedCallback = false
                                    isCaptureButtonEnabled = !isCaptureButtonEnabled
                                    viewModel.coldStartAndTakePhoto(context, owner, false)
                                } else {
                                    coroutineScope.launch {
                                        val images = storageUtil.getOldestImages(context)
                                        images.forEach { image ->
                                            storageUtil.performDeleteImage(
                                                context,
                                                image.first,
                                                image.second
                                            )
                                        }
                                    }
                                    Toast
                                        .makeText(
                                            context,
                                            "Storage is not available, remain $percent%, will delete some files",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.button_take_photo_cold1),
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            if (isJpegSaved == true) {
                                isCaptureButtonEnabled = true
                            }
                        }

                        OutlinedButton(
                            enabled = isCaptureButtonEnabled,
                            modifier = Modifier
                                .align(Alignment.CenterEnd)
                                .scale(0.5f),
                            onClick = {
                                val (available, percent) = storageUtil.isStorageAvailable()
                                if (available) {
                                    isTakePhotoCold = true
                                    isOnImageSavedCallback = true
                                    isCaptureButtonEnabled = !isCaptureButtonEnabled
                                    viewModel.coldStartAndTakePhoto(context, owner, true)
                                } else {
                                    coroutineScope.launch {
                                        val images = storageUtil.getOldestImages(context)
                                        images.forEach { image ->
                                            storageUtil.performDeleteImage(
                                                context,
                                                image.first,
                                                image.second
                                            )
                                        }
                                    }
                                    Toast
                                        .makeText(
                                            context,
                                            "Storage is not available, remain $percent%, will delete some files",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                }
                            }
                        ) {
                            Text(
                                text = stringResource(R.string.button_take_photo_cold2),
                                fontSize = 14.sp,
                                color = Color.White
                            )
                            if (isJpegSaved == true) {
                                isCaptureButtonEnabled = true
                            }
                        }
                    }
                } else {
                    if (activated == true) {
                        OutlinedButton(
                            enabled = isCaptureButtonEnabled,
                            modifier = Modifier
                                .align(Alignment.CenterStart)
                                .scale(0.5f),
                            onClick = {
                                if (!isRecording) {
                                    isCaptureButtonEnabled = !isCaptureButtonEnabled
                                    viewModel.startRecording(context, {
                                        isRecording = true
                                        isCaptureButtonEnabled = true
                                    }, {

                                    })

                                } else {
                                    viewModel.stopRecording()
                                    isRecording = false
                                }
                            }
                        ) {
                            Text(
                                text = if (isRecording) {
                                    stringResource(R.string.button_stop_record)
                                } else {
                                    stringResource(
                                        R.string.button_start_record)
                                },
                                fontSize = 14.sp,
                                color = if (isCameraButtonEnabled) Color.White else Color.Gray
                            )
                        }
                    }
                }

            }
        }
    }
}