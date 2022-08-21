package com.example.videoeditor

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.util.Log
import android.widget.Toast
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.ExecuteCallback
import com.arthenica.mobileffmpeg.FFmpeg
import com.example.videoeditor.colorfilter.ColorFilterList
import com.google.android.exoplayer2.C
import java.io.File
import java.util.*

const val NO_FILTER = 0
const val RED_FILTER = 1
const val GREEN_FILTER = 2
const val BLUE_FILTER = 3
const val RED_FILTER_MULTIPLIER = "1.5"
const val GREEN_FILTER_MULTIPLIER = "1.5"
const val BLUE_FILTER_MULTIPLIER = "1.5"

private const val MAIN_FOLDER_NAME = "Rome4VideoEditor"

class PlayerViewModel : ViewModel() {
    val videoUriLiveData: MutableLiveData<Uri?> = MutableLiveData(null)
    private val videoUri: Uri
        get() = videoUriLiveData.value!!
    val isSavingVideoLiveData: MutableLiveData<Boolean> = MutableLiveData(false)
    var startAutoPlay = true
    var startPosition = C.TIME_UNSET
    val colorFilterList = ColorFilterList().getList()
    private var currentColorFilter = NO_FILTER

    override fun onCleared() {
        super.onCleared()
        clearStartPosition()
    }

    fun initViewModelIfNeed(currentVideoUri: Uri) {
        if (videoUriLiveData.value == null)
            updateVideoUri(currentVideoUri)
    }

    fun updateVideoUri(newUri: Uri) {
        videoUriLiveData.value = newUri
    }

    fun clearStartPosition() {
        startAutoPlay = true
        startPosition = C.TIME_UNSET
    }

    fun updateStartPosition(startAutoPlay: Boolean, startPosition: Long) {
        this.startAutoPlay = startAutoPlay
        this.startPosition = 0L.coerceAtLeast(startPosition)
    }

    fun asyncSaveVideo(context: Context) {
        savingVideoOn()
        createDefaultFolderIfNeed()
        val videoPath = RealPathUtil.getRealPath(context, videoUri)
        val newFileAbsolutePath = getSavingFileNameAndDir()
        FFmpeg.executeAsync("-i \"$videoPath\" \"$newFileAbsolutePath\"", getExecutionCallback(context) {
            showToast(context, "Видео сохранено в $newFileAbsolutePath", Toast.LENGTH_LONG)
        })
    }

    fun asyncSaveWithNewAudio(context: Context, audioUri: Uri) {
        savingVideoOn()
        val videoPath = RealPathUtil.getRealPath(context, videoUri)
        val audioPath = RealPathUtil.getRealPath(context, audioUri)
        val newFileAbsolutePath = getNewTempFileNameAndDir(context)
        FFmpeg.executeAsync(
            "-i \"$videoPath\" -i \"$audioPath\" -c:v copy -map 0:v:0 -map 1:a:0 -shortest \"$newFileAbsolutePath\"",
            getExecutionCallback(context) {
                val newVideoUri = Uri.fromFile(File(newFileAbsolutePath))
                updateVideoUri(newVideoUri)
            }
        )
    }
    fun getColorFilterItemClickListener(context: Context): (Int) -> Unit = {
        savingVideoOn()
        val videoPath = RealPathUtil.getRealPath(context, videoUri)
        val newFileAbsolutePath = getNewTempFileNameAndDir(context)
        val commandParams = createParamsForFilterCommand(colorFilterList[it].colorFilter)
        FFmpeg.executeAsync("-i \"$videoPath\" $commandParams \"$newFileAbsolutePath\"",
            getExecutionCallback(context) {
                currentColorFilter = colorFilterList[it].colorFilter
                val newVideoUri = Uri.fromFile(File(newFileAbsolutePath))
                updateVideoUri(newVideoUri)
            })
    }

    private fun getExecutionCallback(context: Context, successFunc: () -> Unit) =
        ExecuteCallback { _, returnCode ->
            Log.d("debug", "ExecutionCallback is called")
            savingVideoOff()
            when (returnCode) {
                RETURN_CODE_SUCCESS -> successFunc()
                RETURN_CODE_CANCEL -> showToast(context, "Обработка отменена")
                else -> {
                    showToast(context, "Произошла ошибка")
                    Log.i(
                        Config.TAG,
                        String.format(
                            "Async command execution failed with returnCode=%d.",
                            returnCode
                        )
                    )
                }
            }
        }

    private fun getSavingFileNameAndDir() =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            .toString() + "/" + MAIN_FOLDER_NAME + "/VID" + UUID.randomUUID() + ".mp4"

    private fun getNewTempFileNameAndDir(context: Context) =
        context.cacheDir.path + "/" + UUID.randomUUID() + ".mp4"

    private fun createDefaultFolderIfNeed() {
        val file = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                .toString() + "/" + MAIN_FOLDER_NAME
        )
        if (!file.exists()) file.mkdirs()
    }

    private fun showToast(context: Context, message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(context, message, length).show()
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