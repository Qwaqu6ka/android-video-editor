package com.example.videoeditor

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.arthenica.mobileffmpeg.ExecuteCallback
import com.arthenica.mobileffmpeg.FFmpeg
import com.google.android.exoplayer2.C

class PlayerViewModel: ViewModel() {
    lateinit var videoUri: Uri
    var startAutoPlay = true
    var startItemIndex = C.INDEX_UNSET
    var startPosition = C.TIME_UNSET
    var isSavingVideo = false

    override fun onCleared() {
        super.onCleared()
        clearStartPosition()
        Log.d("debug", "model onCleared")
    }

    fun clearStartPosition() {
        startAutoPlay = true
        startItemIndex = C.INDEX_UNSET
        startPosition = C.TIME_UNSET
    }

    fun asyncSaveVideo(uri: Uri, newFileName: String, callback: ExecuteCallback) {
        FFmpeg.executeAsync(
            "-i ${uri.path} $newFileName",
            callback
        )
    }

    fun asyncSaveNewAudio(videoPath: String, audioPath: String, newFilePath: String, callback: ExecuteCallback) {
        FFmpeg.executeAsync(
            "-i $videoPath -i $audioPath -c:v copy -map 0:v:0 -map 1:a:0 -shortest $newFilePath",
            callback
        )
    }
}