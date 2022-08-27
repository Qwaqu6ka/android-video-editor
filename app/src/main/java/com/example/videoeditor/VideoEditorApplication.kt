package com.example.videoeditor

import android.app.Application

class VideoEditorApplication: Application() {
    companion object {
        lateinit var INSTANCE: VideoEditorApplication
    }

    override fun onCreate() {
        super.onCreate()
        INSTANCE = this
    }
}