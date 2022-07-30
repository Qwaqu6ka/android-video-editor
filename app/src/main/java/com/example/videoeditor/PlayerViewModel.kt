package com.example.videoeditor

import android.net.Uri
import android.util.Log
import androidx.lifecycle.ViewModel
import com.arthenica.mobileffmpeg.ExecuteCallback
import com.arthenica.mobileffmpeg.FFmpeg
import com.example.videoeditor.colorfilter.ColorFilterList
import com.google.android.exoplayer2.C

const val NO_FILTER = 0
const val RED_FILTER = 1
const val GREEN_FILTER = 2
const val BLUE_FILTER = 3
const val RED_FILTER_MULTIPLIER = "1.5"
const val GREEN_FILTER_MULTIPLIER = "1.5"
const val BLUE_FILTER_MULTIPLIER = "1.5"

class PlayerViewModel: ViewModel() {
    lateinit var videoUri: Uri
    var startAutoPlay = true
    var startItemIndex = C.INDEX_UNSET
    var startPosition = C.TIME_UNSET
    var isSavingVideo = false
    var colorFilterList = ColorFilterList().getList()
    var currentColorFilter = NO_FILTER

    override fun onCleared() {
        super.onCleared()
        clearStartPosition()
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

    fun asyncSaveNewFilter(videoPath: String, newFileName: String, commandParams:String, callback: ExecuteCallback) {
        FFmpeg.executeAsync(
            "-i $videoPath $commandParams $newFileName",
            callback
        )
    }
    // -vf eq=gamma_r=1:gamma_g=1:gamma_b=1
    fun createParamsForFilterCommand(newFilter: Int): String {
        if (newFilter == currentColorFilter) return ""
        var rMult = "1"; var gMult = "1"; var bMult = "1"
//        when (currentColorFilter) {
//            RED_FILTER -> rMult = "-$RED_FILTER_MULTIPLIER"
//            GREEN_FILTER -> gMult = "-$GREEN_FILTER_MULTIPLIER"
//            BLUE_FILTER -> bMult = "-$BLUE_FILTER_MULTIPLIER"
//        }
        when (newFilter) {
            RED_FILTER -> rMult = RED_FILTER_MULTIPLIER
            GREEN_FILTER -> gMult = GREEN_FILTER_MULTIPLIER
            BLUE_FILTER -> bMult = BLUE_FILTER_MULTIPLIER
        }
        return "-vf eq=gamma_r=$rMult:gamma_g=$gMult:gamma_b=$bMult"
    }
}