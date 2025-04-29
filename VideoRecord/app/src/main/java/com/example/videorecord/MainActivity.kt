package com.example.videorecord
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.provider.Settings
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
    fun MainContent(model: CameraModel) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions(),
            onResult = { isGranted ->
                val missingList = mutableListOf<String>()
                isGranted.entries.forEach {
                    Log.d("RequestPermissions", "[${if (it.value) "granted" else "missing"}] permission: ${it.key}")
                    if (!it.value) missingList.add(it.key)
                }
                model.missingPermissions.value = missingList
            }
        )

        val context = LocalContext.current

        val missingPermissionsState by model.missingPermissions.collectAsState()
        val missingPermissions = missingPermissionsState
        if (missingPermissions == null) {
            SideEffect { permissionLauncher.launch(model.REQUIRED_PERMISSIONS)
            }
        } else {
            if (!hasManageExternalStoragePermission(context)) {
                Scaffold { innerPadding ->
                    Column(
                        modifier = Modifier
                            .padding(innerPadding)
                            .fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text("Missing permissions: MANAGE_ALL_FILES_ACCESS_PERMISSION\n Please turn on in settings")
                        Button(
                            modifier = Modifier.fillMaxWidth(),
                            content = { Text("Request Permissions") },
                            onClick = {
                                if (!hasManageExternalStoragePermission(context)) {
                                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                                    try {
                                        startActivityForResult(intent, 1)
                                        finish()
                                    } catch (e: Exception) {
                                        e.printStackTrace()
                                    }
                                } else {
                                    permissionLauncher.launch(model.REQUIRED_PERMISSIONS)
                                }
                            },
                        )
                    }
                }
            } else {
                VideoRecorderApp(model)
            }
        }
    }

    private fun hasManageExternalStoragePermission(context: Context): Boolean {
        return Environment.isExternalStorageManager()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode == 1) {
            if (hasManageExternalStoragePermission(this)) {
                Log.d(TAG, "User granted MANAGE_EXTERNAL_STORAGE permission")
            } else {
                Log.d(TAG, "User did not grant MANAGE_EXTERNAL_STORAGE permission")
            }
        }
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
        var isRecording by remember { mutableStateOf(false) }
        var showDialog by remember { mutableStateOf(false) }
        val recordingFile by model.mFileName.collectAsState()
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
                    showDialog = true
                },
                    enabled = recordingFile != null && !isRecording) {
                    Text("Last Video")
                }
            }
        }
        if (showDialog) {
            model.mFileName.also { VideoDialog(onDismissRequest = { showDialog = false }, it.value!!) }
        }
    }

}
