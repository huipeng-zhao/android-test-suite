package com.example.cameratest.navigation

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.lifecycle.LifecycleOwner
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.example.cameratest.ui.compose.CameraPreview
import com.example.cameratest.ui.compose.GalleryView
import com.example.cameratest.ui.theme.CameraTestTheme
import com.example.cameratest.viewmodel.CameraViewModel

@Composable
fun NavGraph(navController: NavHostController,
             context: Context,
             owner: LifecycleOwner,
             viewModel: CameraViewModel,
             onFinish: () -> Unit) {
    NavHost(
        navController = navController,
        startDestination = NavRoute.Main.path
    ) {
        addMainView(navController, this, context, owner, viewModel, onFinish)

        addGalleryView(navController, this, context, viewModel)
    }
}

private fun addMainView(
    navController: NavHostController,
    navGraphBuilder: NavGraphBuilder,
    context: Context,
    owner: LifecycleOwner,
    viewModel: CameraViewModel,
    onFinish: () -> Unit
) {
    navGraphBuilder.composable(route = NavRoute.Main.path) {
        CameraTestTheme {
            CameraPreview(context, owner, viewModel, onFinish, {
                navController.navigate(NavRoute.Gallery.path)
            })
        }
    }
}

private fun addGalleryView(
    navController: NavHostController,
    navGraphBuilder: NavGraphBuilder,
    context: Context,
    viewModel: CameraViewModel) {
    navGraphBuilder.composable(route = NavRoute.Gallery.path) {
        GalleryView(context, viewModel) {
            navController.navigate(NavRoute.Main.path)
        }
    }
}