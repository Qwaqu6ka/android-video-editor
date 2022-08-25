package com.example.videoeditor

import android.net.Uri
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.ExecuteCallback
import com.arthenica.mobileffmpeg.FFmpeg
import com.example.videoeditor.colorfilter.ColorFilterList
import com.example.videoeditor.fileutils.FileSystemUtil
import com.example.videoeditor.fileutils.RealPathUtil
import com.google.android.exoplayer2.C
import java.io.File

const val NO_FILTER = 0
const val RED_FILTER = 1
const val GREEN_FILTER = 2
const val BLUE_FILTER = 3
const val RED_FILTER_MULTIPLIER = "1.5"
const val GREEN_FILTER_MULTIPLIER = "1.5"
const val BLUE_FILTER_MULTIPLIER = "1.5"

class PlayerViewModel : ViewModel() {

    companion object {
        const val SUCCESS_SAVING_TOAST_MESSAGE_CODE = 0
        const val CANCEL_SAVING_TOAST_MESSAGE_CODE = 1
        const val ERROR_SAVING_TOAST_MESSAGE_CODE = 2
    }

    val changeVideoUriSingleLiveEvent = SingleLiveEvent<Uri>()
    private val videoUri: Uri
        get() = changeVideoUriSingleLiveEvent.value!!
    val isSavingVideoLiveData: MutableLiveData<Boolean> = MutableLiveData(false)
    val nextToastMessageCodeSingleLiveEvent = SingleLiveEvent<Int>()
    var startAutoPlay = true
    var startPosition = C.TIME_UNSET
    val colorFilterList = ColorFilterList().getList()
    private var currentColorFilter = NO_FILTER

    override fun onCleared() {
        super.onCleared()
        clearStartPosition()
    }

    fun initViewModelIfNeed(currentVideoUri: Uri) {
        if (changeVideoUriSingleLiveEvent.value == null)
            updateVideoUri(currentVideoUri)
    }

    fun updateVideoUri(newUri: Uri) {
        changeVideoUriSingleLiveEvent.value = newUri
    }

    fun clearStartPosition() {
        startAutoPlay = true
        startPosition = C.TIME_UNSET
    }

    fun updateStartPosition(startAutoPlay: Boolean, startPosition: Long) {
        this.startAutoPlay = startAutoPlay
        this.startPosition = 0L.coerceAtLeast(startPosition)
    }

    fun asyncSaveVideo() {
        savingVideoOn()
        FileSystemUtil.createDefaultFolderIfNeed()
        val videoPath = RealPathUtil.getRealPath(videoUri)
        val newFileAbsolutePath = FileSystemUtil.getSavingFileNameAndDir()
        FFmpeg.executeAsync("-i \"$videoPath\" \"$newFileAbsolutePath\"", getExecutionCallback {
            nextToastMessageCodeSingleLiveEvent.value = SUCCESS_SAVING_TOAST_MESSAGE_CODE
        })
    }

    fun asyncSaveWithNewAudio(audioUri: Uri) {
        savingVideoOn()
        val videoPath = RealPathUtil.getRealPath(videoUri)
        val audioPath = RealPathUtil.getRealPath(audioUri)
        val newFileAbsolutePath = FileSystemUtil.getNewTempFileNameAndDir()
        FFmpeg.executeAsync(
            "-i \"$videoPath\" -i \"$audioPath\" -c:v copy -map 0:v:0 -map 1:a:0 -shortest \"$newFileAbsolutePath\"",
            getExecutionCallback {
                val newVideoUri = Uri.fromFile(File(newFileAbsolutePath))
                updateVideoUri(newVideoUri)
            }
        )
    }

    fun getColorFilterItemClickListener(): (Int) -> Unit = {
        savingVideoOn()
        val videoPath = RealPathUtil.getRealPath(videoUri)
        val newFileAbsolutePath = FileSystemUtil.getNewTempFileNameAndDir()
        val commandParams = createParamsForFilterCommand(colorFilterList[it].colorFilter)
        FFmpeg.executeAsync("-i \"$videoPath\" $commandParams \"$newFileAbsolutePath\"",
            getExecutionCallback {
                currentColorFilter = colorFilterList[it].colorFilter
                val newVideoUri = Uri.fromFile(File(newFileAbsolutePath))
                updateVideoUri(newVideoUri)
            })
    }

    private fun getExecutionCallback(successFunc: () -> Unit) =
        ExecuteCallback { _, returnCode ->
            savingVideoOff()
            when (returnCode) {
                RETURN_CODE_SUCCESS -> successFunc()
                RETURN_CODE_CANCEL -> nextToastMessageCodeSingleLiveEvent.value =
                    CANCEL_SAVING_TOAST_MESSAGE_CODE
                else -> {
                    nextToastMessageCodeSingleLiveEvent.value = ERROR_SAVING_TOAST_MESSAGE_CODE
//                    Log.i(
//                        Config.TAG,
//                        String.format(
//                            "Async command execution failed with returnCode=%d.",
//                            returnCode
//                        )
//                    )
                }
            }
        }


    private fun savingVideoOn() {
        isSavingVideoLiveData.value = true
    }

    private fun savingVideoOff() {
        isSavingVideoLiveData.value = false
    }

    private fun createParamsForFilterCommand(newFilter: Int): String {
        if (newFilter == currentColorFilter) return ""
        var rMult = "1"
        var gMult = "1"
        var bMult = "1"
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