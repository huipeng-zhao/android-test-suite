package com.example.cameratest.navigation

sealed class NavRoute(val path: String) {
    object Main: NavRoute("main")
    object Gallery: NavRoute("gallery")
}