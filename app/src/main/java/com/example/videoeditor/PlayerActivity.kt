package com.example.videoeditor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_CANCEL
import com.arthenica.mobileffmpeg.Config.RETURN_CODE_SUCCESS
import com.arthenica.mobileffmpeg.ExecuteCallback
import com.arthenica.mobileffmpeg.FFmpeg
import com.example.videoeditor.databinding.ActivityPlayerBinding
import com.google.android.exoplayer2.C
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.ProgressiveMediaSource
import com.google.android.exoplayer2.trackselection.TrackSelectionParameters
import com.google.android.exoplayer2.ui.StyledPlayerView
import com.google.android.exoplayer2.upstream.DataSource
import com.google.android.exoplayer2.upstream.DefaultDataSource
import com.google.android.exoplayer2.util.Util
import java.io.*

private const val MAIN_FOLDER_NAME = "Rome4VideoEditor"

private const val EXTRA_VIDEO_URI = "com.example.videoeditor.video_uri"
private const val AUDIO_CODE = 1
private const val VIDEO_CODE = 2

// Saved instance state keys.
private const val KEY_IS_SAVING_VIDEO = "com.example.videoeditor.is_saving_video"
private const val KEY_VIDEO_URI = "com.example.videoeditor.video_uri"
private const val KEY_ITEM_INDEX = "com.example.videoeditor.item_index"
private const val KEY_POSITION = "com.example.videoeditor.position"
private const val KEY_AUTO_PLAY = "com.example.videoeditor.auto_play"
private const val KEY_TRACK_SELECTION_PARAMETERS =
    "com.example.videoeditor.track_selection_parameters"

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
    private lateinit var videoUri: Uri
    private lateinit var trackSelectionParameters: TrackSelectionParameters
    private var startAutoPlay = false
    private var startItemIndex = 0
    private var startPosition: Long = 0L
    private var isSavingVideo = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.playerView.setControllerVisibilityListener(this)
        binding.playerView.requestFocus()
        binding.pickVideoButton.setOnClickListener(pickVideo())
        binding.pickAudioButton.setOnClickListener(pickAudio())
        binding.saveVideoButton.setOnClickListener(saveVideo())

        if (savedInstanceState != null) {
            videoUri = savedInstanceState.getParcelable(KEY_VIDEO_URI)!!
            startAutoPlay = savedInstanceState.getBoolean(KEY_AUTO_PLAY)
            startItemIndex = savedInstanceState.getInt(KEY_ITEM_INDEX)
            startPosition = savedInstanceState.getLong(KEY_POSITION)
            isSavingVideo = savedInstanceState.getBoolean(KEY_IS_SAVING_VIDEO)
            trackSelectionParameters = TrackSelectionParameters.fromBundle(
                savedInstanceState.getBundle(KEY_TRACK_SELECTION_PARAMETERS)!!
            )
        } else {
            videoUri = intent.getParcelableExtra(EXTRA_VIDEO_URI)!!
            trackSelectionParameters = TrackSelectionParameters.Builder(this).build()
            clearStartPosition()
        }

        if (isSavingVideo)
            turnOnLoadingScreen()
        else
            turnOffLoadingScreen()
    }

    public override fun onStart() {
        super.onStart()
        if (Util.SDK_INT > 23) {
            initializePlayer()
            binding.playerView.onResume()
        }
    }

    public override fun onResume() {
        super.onResume()
        if (Util.SDK_INT <= 23) {
            initializePlayer()
            binding.playerView.onResume()
        }
    }

    public override fun onPause() {
        super.onPause()
        if (Util.SDK_INT <= 23) {
            binding.playerView.onPause()
            releasePlayer()
        }
    }

    public override fun onStop() {
        super.onStop()
        if (Util.SDK_INT > 23) {
            binding.playerView.onPause()
            releasePlayer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        updateTrackSelectorParameters()
        updateStartPosition()
        outState.putBundle(
            KEY_TRACK_SELECTION_PARAMETERS,
            trackSelectionParameters.toBundle()
        )
        outState.putParcelable(KEY_VIDEO_URI, videoUri)
        outState.putBoolean(KEY_AUTO_PLAY, startAutoPlay)
        outState.putInt(KEY_ITEM_INDEX, startItemIndex)
        outState.putLong(KEY_POSITION, startPosition)
        outState.putBoolean(KEY_IS_SAVING_VIDEO, isSavingVideo)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        releasePlayer()
        clearStartPosition()
        setIntent(intent)
        initializePlayer()
    }

    private fun initializePlayer() {
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(this)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(videoUri))
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player.trackSelectionParameters = trackSelectionParameters
        player.playWhenReady = startAutoPlay
        player.repeatMode = Player.REPEAT_MODE_ONE
        binding.playerView.player = player

        val haveStartPosition = startItemIndex != C.INDEX_UNSET
        if (haveStartPosition) {
            player.seekTo(startItemIndex, startPosition)
        }
        player.setMediaSource(mediaSource, !haveStartPosition)
        player.prepare()
    }

    private fun releasePlayer() {
        updateTrackSelectorParameters()
        updateStartPosition()
        player.release()
        binding.playerView.player = null
    }

    private fun clearStartPosition() {
        startAutoPlay = true
        startItemIndex = C.INDEX_UNSET
        startPosition = C.TIME_UNSET
    }

    override fun onVisibilityChanged(visibility: Int) {
        binding.controlsRoot.visibility = visibility
    }

    private fun updateTrackSelectorParameters() {
        trackSelectionParameters = player.trackSelectionParameters
    }

    private fun updateStartPosition() {
        startAutoPlay = player.playWhenReady
        startItemIndex = player.currentMediaItemIndex
        startPosition = 0L.coerceAtLeast(player.contentPosition)
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
        turnOnLoadingScreen()
        createDefaultFolderIfNeed()
        val newFileName =
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES)
                .toString() + "/" + MAIN_FOLDER_NAME + "/VID" + System.currentTimeMillis() + ".mp4"
        FFmpeg.executeAsync(
            "-i ${videoUri.path} $newFileName",
            saveVideoExecuteCallback{
                showToast("Видео сохранено в $newFileName", Toast.LENGTH_LONG)
            }
        )
    }

    private fun saveVideoExecuteCallback(successResultFunc: () -> Unit) =
        ExecuteCallback { _, returnCode ->
            turnOffLoadingScreen()
            when (returnCode) {
                RETURN_CODE_SUCCESS -> successResultFunc()
                RETURN_CODE_CANCEL -> showToast("Обработка отменена")
                else -> showToast("Произошла ошибка")
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
            val intent = newPlayerIntent(this@PlayerActivity, it)
            startActivity(intent)
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
            turnOnLoadingScreen()
            val currentVideoPath = RealPathUtil.getRealPath(
                this,
                videoUri
            )
            val currentAudioPath = RealPathUtil.getRealPath(
                this,
                it
            )
            val newFileName =
                cacheDir.path + "/" + System.currentTimeMillis() + ".mp4"
            FFmpeg.executeAsync(
                "-i $currentVideoPath -i $currentAudioPath -c:v copy -map 0:v:0 -map 1:a:0 -shortest $newFileName",
                saveVideoExecuteCallback {
                    releasePlayer()
                    clearStartPosition()
                    val newVideoUri = Uri.fromFile(File(newFileName))
                    videoUri = newVideoUri!!
                    initializePlayer()
                }
            )
        }

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
        isSavingVideo = true
        binding.playerView.visibility = View.GONE
        binding.loadingText.visibility = View.VISIBLE
        player.playWhenReady = false
    }

    private fun turnOffLoadingScreen() {
        isSavingVideo = false
        binding.playerView.visibility = View.VISIBLE
        binding.loadingText.visibility = View.GONE
        player.playWhenReady = true
    }
}
