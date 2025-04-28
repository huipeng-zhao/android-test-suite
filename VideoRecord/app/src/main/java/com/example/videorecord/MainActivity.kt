package com.example.videorecord
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import java.io.File
import kotlin.collections.isNotEmpty
import kotlin.collections.joinToString

class MainActivity : ComponentActivity() {
    private val TAG = MainActivity::class.java.simpleName

    var myModel: CameraModel? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val app = application as MainApplication
        val model = app.sharedModel

        myModel = model

        setContent {
            MainContent(model)
        }
    }


    @Composable
    fun MainContent(model : CameraModel) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { isGranted ->
                val missingList = mutableListOf<String>()
                isGranted.entries.forEach {
                    Log.d("RequestPermissions", "[${if (it.value) "granted" else "missing"}] permission: ${it.key}")
                    if (!it.value) missingList.add(it.key)
                }
                if (missingList.isEmpty()) {
                    initAfterPermissionsGranted()
                }
                model.missingPermissions.value = missingList
            }
        )
        val missingPermissionsState by model.missingPermissions.collectAsState()
        val missingPermissions = missingPermissionsState
        if (missingPermissions == null) {
            SideEffect { permissionLauncher.launch(model.REQUIRED_PERMISSIONS) }
        } else {
            if (missingPermissions.isNotEmpty()) {
                Scaffold { innerPadding ->
                    Column(
                        modifier = Modifier.padding(innerPadding).fillMaxSize(),
                        verticalArrangement = Arrangement.Center
                    ) {
                        Text("Missing permissions:\n" + missingPermissions.joinToString("\n") { "- " + it.replaceFirst("android.permission.", "") })
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            content = { Text("Request Permissions") },
                            onClick = { permissionLauncher.launch(model.REQUIRED_PERMISSIONS) },
                        )
                    }
                }

            } else {
                VideoRecorderApp(model)
            }
        }
    }

    fun initAfterPermissionsGranted() {
        Log.d(TAG, "permission allow")
    }

    @Composable
    private fun VideoDialog(onDismissRequest: () -> Unit, videoFile: File) {
        val context = LocalContext.current
        val player = remember(context) {
            ExoPlayer.Builder(context).build().apply {
                setMediaItem(MediaItem.fromUri(Uri.fromFile(videoFile )))
                prepare()
                playWhenReady = true
            }
        }

        DisposableEffect(player) {
            onDispose {
                player.release()
            }
        }

        Dialog(onDismissRequest = onDismissRequest) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        layoutParams = FrameLayout.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.WRAP_CONTENT
                        )
                        this.player = player
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
    }

    @Composable
    private fun ImageDialog(imageByteArray: ByteArray, onDismiss: () -> Unit) {
        val bitmap = android.graphics.BitmapFactory.decodeByteArray(imageByteArray, 0, imageByteArray.size)

        Dialog(onDismissRequest = onDismiss) {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }
    }
    @Composable
    fun VideoRecorderApp(model: CameraModel) {
        val context = LocalContext.current
        var isRecording by remember { mutableStateOf(false) }
        var showDialog by remember { mutableStateOf(false) }
        var showPhotoDialog by remember { mutableStateOf(false) }
        val recordingFile by model.mFileName.collectAsState()
        val imageFile by model.mImageData.collectAsState(initial = byteArrayOf())
        var lastPhoto by remember { mutableStateOf(byteArrayOf()) }
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (!isRecording) {
                    Button(onClick = {
                        model.startRecording()
                        isRecording = true
                    }) {
                        Text("Start Recording")
                    }
                } else {
                    Button(onClick = {
                        model.stopRecording().let { file ->
                            isRecording = false
                        }
                    }) {
                        Text("Stop Recording")
                    }
                }

                Button(onClick = {
                    model.capturePhoto()
                }) {
                    Text("Capture")
                }

                Button(onClick = {
                    showDialog = true
                },
                    enabled = recordingFile != null && !isRecording) {
                    Text("Last Video")
                }

                Button(onClick = {
                    showPhotoDialog = true },
                    enabled = !imageFile.isEmpty()) {
                    Text("Last Photo")
                }
            }
        }
        if (showDialog) {
            model.mFileName.also { VideoDialog(onDismissRequest = { showDialog = false }, it.value!!) }
        }

        if (showPhotoDialog) {
            ImageDialog(imageFile) {
                showPhotoDialog = false
                lastPhoto = byteArrayOf()
            }
        }
    }

}
