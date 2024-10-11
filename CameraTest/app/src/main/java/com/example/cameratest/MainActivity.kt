package com.example.cameratest

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.ViewModelProvider
import androidx.navigation.compose.rememberNavController
import com.example.cameratest.navigation.NavGraph
import com.example.cameratest.viewmodel.CameraViewModel
import com.example.cameratest.ui.compose.CameraPreview
import com.example.cameratest.ui.theme.CameraTestTheme

class MainActivity : ComponentActivity() {
    private lateinit var viewModel: CameraViewModel
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewModel = ViewModelProvider(this).get(CameraViewModel::class)

        enableEdgeToEdge()
        setContent {
            val navController = rememberNavController()
            NavGraph(navController, this, this, viewModel) { finish() }
//            CameraTestTheme {
//                CameraPreview(this, this, viewModel) { finish() }
//            }
        }
    }

    override fun onPause() {
        super.onPause()
        viewModel.stopCameraPreview()
    }
}
