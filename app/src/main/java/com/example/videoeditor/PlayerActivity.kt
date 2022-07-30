package com.example.videoeditor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.arthenica.mobileffmpeg.Config
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.ExecuteCallback
import com.example.videoeditor.colorfilter.ColorFilterRecyclerAdapter
import com.example.videoeditor.databinding.ActivityPlayerBinding
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.util.Util
import java.io.*

private const val MAIN_FOLDER_NAME = "Rome4VideoEditor"

private const val EXTRA_VIDEO_URI = "com.example.videoeditor.video_uri"
private const val AUDIO_CODE = 1
private const val VIDEO_CODE = 2

class PlayerActivity : AppCompatActivity(), StyledPlayerView.ControllerVisibilityListener {

    companion object {
        fun newPlayerIntent(context: Context, uri: Uri): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_VIDEO_URI, uri)
            }
        }
    }

    private lateinit var player: ExoPlayer
    private lateinit var binding: ActivityPlayerBinding
    private lateinit var viewModelLink: PlayerViewModel

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("debug", "onCreate")
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val model: PlayerViewModel by viewModels()
        viewModelLink = model
        viewModelLink.videoUri = intent.getParcelableExtra(EXTRA_VIDEO_URI)!!

        binding.playerView.setControllerVisibilityListener(this)
        binding.playerView.requestFocus()
        binding.pickVideoButton.setOnClickListener(pickVideo())
        binding.pickAudioButton.setOnClickListener(pickAudio())
        binding.saveVideoButton.setOnClickListener(saveVideo())
        binding.colorFilterButton.setOnClickListener(changeColorFilterListVisibility())
        binding.colorFilterRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.colorFilterRecycler.adapter =
            ColorFilterRecyclerAdapter(viewModelLink.colorFilterList, colorFilterItemClickListener)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d("debug", "onDestroy")
    }

    public override fun onStart() {
        super.onStart()
        Log.d("debug", "onStart")
        if (Util.SDK_INT > 23) {
            initializePlayer()
        }
    }

    public override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= 23) {
            initializePlayer()
        }
    }

    public override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            releasePlayer()
        }
    }

    public override fun onStop() {
        super.onStop()
        Log.d("debug", "onStop")
        if (Util.SDK_INT > 23) {
            releasePlayer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        updateStartPosition()
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        Log.d("debug", "onNewIntent ${viewModelLink.videoUri}")
        releasePlayer()
        viewModelLink.clearStartPosition()
        setIntent(intent)
        viewModelLink.videoUri = intent?.getParcelableExtra(EXTRA_VIDEO_URI)!!
        initializePlayer()
    }

    override fun onBackPressed() {
        if (binding.colorFilterRecycler.visibility == View.VISIBLE)
            binding.colorFilterRecycler.visibility = View.GONE
        else
            super.onBackPressed()
    }

    override fun onVisibilityChanged(visibility: Int) {
        binding.controlsRoot.visibility = visibility
        binding.colorFilterRecycler.visibility = View.GONE
    }

    private fun initializePlayer() {
        Log.d("debug", "initPlayer ${viewModelLink.videoUri}")
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(this)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(viewModelLink.videoUri))
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player.playWhenReady = viewModelLink.startAutoPlay
        player.repeatMode = Player.REPEAT_MODE_ONE
        binding.playerView.player = player

        val haveStartPosition = viewModelLink.startItemIndex != C.INDEX_UNSET
        if (haveStartPosition) {
            player.seekTo(viewModelLink.startItemIndex, viewModelLink.startPosition)
        }
        player.setMediaSource(mediaSource, !haveStartPosition)
        Log.d("debug", "init: isSavingVideo == ${viewModelLink.isSavingVideo}")
        if (viewModelLink.isSavingVideo) {
            Log.d("debug", "turnOnLoadingScreen() from init")
            turnOnLoadingScreen()
        }
        player.prepare()
    }

    private fun releasePlayer() {
        Log.d("debug", "releasePlayer ${viewModelLink.videoUri}")
        updateStartPosition()
        player.release()
        binding.playerView.player = null
    }

    private fun updateStartPosition() {
        viewModelLink.startAutoPlay = player.playWhenReady
        viewModelLink.startItemIndex = player.currentMediaItemIndex
        viewModelLink.startPosition = 0L.coerceAtLeast(player.contentPosition)
    }

    private fun changeColorFilterListVisibility() = View.OnClickListener {
        if (binding.colorFilterRecycler.visibility == View.GONE)
            binding.colorFilterRecycler.visibility = View.VISIBLE
        else
            binding.colorFilterRecycler.visibility = View.GONE
    }

    private fun pickVideo() = View.OnClickListener {
        videoStoragePermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    private fun pickAudio() = View.OnClickListener {
        audioStoragePermissionLauncher.launch(
            arrayOf(
                Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
        )
    }

    private fun saveVideo() = View.OnClickListener {
        Log.d("debug", "turnOnLoadingScreen() from saveVideo button")
        turnOnLoadingScreen()
        createDefaultFolderIfNeed()
        val newFileAbsolutePath = getSavingFileNameAndDir()
        viewModelLink.asyncSaveVideo(
            viewModelLink.videoUri,
            newFileAbsolutePath,
            saveVideoExecuteCallback {
                showToast("Видео сохранено в $newFileAbsolutePath", Toast.LENGTH_LONG)
            }
        )
    }

    private val colorFilterItemClickListener: (Int) -> Unit = {
        turnOnLoadingScreen()
        val newFileAbsolutePath = getNewTempFileNameAndDir()
        val commandParams: String
        viewModelLink.apply {
            commandParams = createParamsForFilterCommand(colorFilterList[it].colorFilter)
        }
        viewModelLink.asyncSaveNewFilter(
            RealPathUtil.getRealPath(this, viewModelLink.videoUri)!!,
            newFileAbsolutePath,
            commandParams,
            saveVideoExecuteCallback {
                viewModelLink.apply { currentColorFilter = colorFilterList[it].colorFilter }
                val newVideoUri = Uri.fromFile(File(newFileAbsolutePath))
                val intent = newPlayerIntent(this@PlayerActivity, newVideoUri)
                Log.d("debug", "success callback is called ${viewModelLink.videoUri}")
                startActivity(intent)
            }
        )
    }

    private fun saveVideoExecuteCallback(successResultFunc: () -> Unit) =
        ExecuteCallback { _, returnCode ->
            turnOffLoadingScreen()
            when (returnCode) {
                RETURN_CODE_SUCCESS -> successResultFunc()
                RETURN_CODE_CANCEL -> showToast("Обработка отменена")
                else -> {
                    showToast("Произошла ошибка")
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

    private val videoStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            when {
                granted -> {
                    chooseVideoLauncher.launch("video/*")
                }
                !shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> showPermissionDeniedDialog()
                else -> showRationaleDialog(VIDEO_CODE)
            }
        }

    private val chooseVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it != null) {
                val intent = newPlayerIntent(this@PlayerActivity, it)
                startActivity(intent)
            }
        }

    private val audioStoragePermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            when {
                granted -> {
                    chooseAudioLauncher.launch("audio/*")
                }
                !shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> showPermissionDeniedDialog()
                else -> showRationaleDialog(AUDIO_CODE)
            }
        }

    private val chooseAudioLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it == null) return@registerForActivityResult
            val videoPath = RealPathUtil.getRealPath(
                this,
                viewModelLink.videoUri
            )
            val audioPath = RealPathUtil.getRealPath(
                this,
                it
            )
            val newFileAbsolutePath = getNewTempFileNameAndDir()
            if (videoPath == null || audioPath == null) return@registerForActivityResult
            Log.d("debug", "turnOnLoadingScreen() from chooseAudioLauncher")
            turnOnLoadingScreen()
            viewModelLink.asyncSaveNewAudio(
                videoPath,
                audioPath,
                newFileAbsolutePath,
                saveVideoExecuteCallback {
                    val newVideoUri = Uri.fromFile(File(newFileAbsolutePath))
                    val intent = newPlayerIntent(this@PlayerActivity, newVideoUri)
                    Log.d("debug", "success callback is called ${viewModelLink.videoUri}")
                    startActivity(intent)
                }
            )
        }

    private fun getSavingFileNameAndDir() =
        Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
            .toString() + "/" + MAIN_FOLDER_NAME + "/VID" + System.currentTimeMillis() + ".mp4"

    private fun getNewTempFileNameAndDir() =
        cacheDir.path + "/" + System.currentTimeMillis() + ".mp4"

    private fun showPermissionDeniedDialog() {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.permission_denied_dialog_message)
            .setPositiveButton(R.string.settings) { _, _ ->
                val uri = Uri.fromParts("package", packageName, null)
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri)
                ContextCompat.startActivity(this, intent, null)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private fun showRationaleDialog(code: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.storage_permission_rationale)
            .setPositiveButton(R.string.allow) { _, _ ->
                if (code == AUDIO_CODE)
                    videoStoragePermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
                else
                    audioStoragePermissionLauncher.launch(
                        arrayOf(
                            Manifest.permission.READ_EXTERNAL_STORAGE,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        )
                    )
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private fun createDefaultFolderIfNeed() {
        val f = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                .toString() + "/" + MAIN_FOLDER_NAME
        )
        if (!f.exists()) f.mkdirs()
    }

    private fun showToast(message: String, length: Int = Toast.LENGTH_SHORT) {
        Toast.makeText(this@PlayerActivity, message, length).show()
    }

    private fun turnOnLoadingScreen() {
        viewModelLink.isSavingVideo = true
        binding.playerView.visibility = View.GONE
        binding.controlsRoot.visibility = View.GONE
        binding.loadingText.visibility = View.VISIBLE
        player.playWhenReady = false
        viewModelLink.startAutoPlay = false
        Log.d("debug", "turnOnLoadingScreen()")
    }

    private fun turnOffLoadingScreen() {
        viewModelLink.isSavingVideo = false
        binding.playerView.visibility = View.VISIBLE
        binding.loadingText.visibility = View.GONE
        player.playWhenReady = true
        viewModelLink.startAutoPlay = true
        Log.d("debug", "turnOffLoadingScreen()")
    }

    // TODO: 1) Что-то не так с controls_root 2)при перевороте всё норм становится
}
