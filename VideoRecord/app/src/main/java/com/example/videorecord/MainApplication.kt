package com.example.videorecord

import android.app.Application
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner

class MainApplication : Application(), ViewModelStoreOwner {
    private val appViewModelStore: ViewModelStore by lazy { ViewModelStore() }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore

    val sharedModel: CameraModel by lazy {
        ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(this))[CameraModel::class.java]
    }
}