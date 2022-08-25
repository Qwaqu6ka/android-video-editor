package com.example.videoeditor.fileutils

import android.os.Environment
import com.example.videoeditor.VideoEditorApplication
import java.io.File
import java.util.*

private const val MAIN_FOLDER_NAME = "Rome4VideoEditor"

class FileSystemUtil {
    companion object {
        fun getSavingFileNameAndDir() =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                .toString() + "/" + MAIN_FOLDER_NAME + "/VID" + UUID.randomUUID() + ".mp4"

        fun getNewTempFileNameAndDir() =
            VideoEditorApplication.INSTANCE.cacheDir.path + "/" + UUID.randomUUID() + ".mp4"

        fun createDefaultFolderIfNeed() {
            val file = File(
                Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                    .toString() + "/" + MAIN_FOLDER_NAME
            )
            if (!file.exists()) file.mkdirs()
        }
    }
}