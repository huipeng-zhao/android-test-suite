package com.example.cameratest

import android.app.Application
import androidx.camera.camera2.Camera2Config
import androidx.camera.core.CameraSelector
import androidx.camera.core.CameraXConfig
import com.example.cameratest.camera.CameraController
import java.util.concurrent.Executors

class MainApplication : Application(), CameraXConfig.Provider {
    override fun getCameraXConfig(): CameraXConfig {
        return CameraXConfig.Builder.fromConfig(Camera2Config.defaultConfig())
            // Tell CameraX to only initialize the selected camera.
            .setAvailableCamerasLimiter(CameraSelector.Builder().requireLensFacing(CameraController.LENS_FACING).build())
            // Do not use the main thread.
            .setCameraExecutor(Executors.newSingleThreadExecutor())
            .build()
    }
}