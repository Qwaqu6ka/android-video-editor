package com.example.videoeditor

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
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

private const val EXTRA_VIDEO_URI = "com.example.videoeditor.video_uri"
private const val AUDIO_CONTENT_TYPE = 1
private const val VIDEO_CONTENT_TYPE = 2

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
    private val playerViewModel: PlayerViewModel by lazy { ViewModelProvider(this)[PlayerViewModel::class.java] }
    private lateinit var currentVideoUri: Uri

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Log.d("debug", "onCreate")
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        currentVideoUri = intent.getParcelableExtra(EXTRA_VIDEO_URI)!!
        playerViewModel.initViewModelIfNeed(currentVideoUri)

        playerViewModel.videoUriLiveData.observe(this) {
            if (it != currentVideoUri) {
                val intent = newPlayerIntent(this@PlayerActivity, it!!)
                startActivity(intent)
            }
        }
        playerViewModel.isSavingVideoLiveData.observe(this) {
            if (it) {
                turnOnLoadingScreen()
            } else
                turnOffLoadingScreen()
        }

        binding.playerView.setControllerVisibilityListener(this)
        binding.playerView.requestFocus()
        binding.pickVideoButton.setOnClickListener { pickMediaContent(VIDEO_CONTENT_TYPE) }
        binding.pickAudioButton.setOnClickListener { pickMediaContent(AUDIO_CONTENT_TYPE) }
        binding.saveVideoButton.setOnClickListener { playerViewModel.asyncSaveVideo(this@PlayerActivity) }
        binding.colorFilterButton.setOnClickListener(changeColorFilterListVisibility())
        binding.colorFilterRecycler.layoutManager =
            LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        binding.colorFilterRecycler.adapter =
            ColorFilterRecyclerAdapter(
                playerViewModel.colorFilterList,
                playerViewModel.getColorFilterItemClickListener(this@PlayerActivity)
            )
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
            Log.d("debug", "onPauseReleasePlayer $currentVideoUri")
            releasePlayer()
        }
    }

    public override fun onStop() {
        super.onStop()
        Log.d("debug", "onStop")
        if (Util.SDK_INT > 23) {
            Log.d("debug", "onStopReleasePlayer $currentVideoUri")
            releasePlayer()
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        playerViewModel.updateStartPosition(player.playWhenReady, player.contentPosition)
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (haveDifferentExtras(intent, this.intent)) {
            Log.d("debug", "onNewIntent")
            Log.d("debug", "old intent: ${this.intent.getParcelableExtra<Uri>(EXTRA_VIDEO_URI)!!}")
            Log.d("debug", "new intent: ${intent?.getParcelableExtra<Uri>(EXTRA_VIDEO_URI)!!}")
            setIntent(intent)
            releasePlayer()
            playerViewModel.clearStartPosition()
            currentVideoUri = this.intent.getParcelableExtra(EXTRA_VIDEO_URI)!!
            initializePlayer()
        }
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
        Log.d("debug", "initPlayer $currentVideoUri")
        val dataSourceFactory: DataSource.Factory = DefaultDataSource.Factory(this)
        val mediaSource = ProgressiveMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(currentVideoUri))
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
        player.playWhenReady = playerViewModel.startAutoPlay
        player.repeatMode = Player.REPEAT_MODE_ONE
        binding.playerView.player = player

        val haveStartPosition = playerViewModel.startPosition != C.TIME_UNSET
        if (haveStartPosition) {
            player.seekTo(playerViewModel.startPosition)
        }
        player.setMediaSource(mediaSource, !haveStartPosition)
        player.prepare()
    }

    private fun releasePlayer() {
        playerViewModel.updateStartPosition(player.playWhenReady, player.contentPosition)
        player.release()
        binding.playerView.player = null
    }

    private fun changeColorFilterListVisibility() = View.OnClickListener {
        if (binding.colorFilterRecycler.visibility == View.GONE)
            binding.colorFilterRecycler.visibility = View.VISIBLE
        else
            binding.colorFilterRecycler.visibility = View.GONE
    }

    private fun pickMediaContent(mediaContentType: Int) {
        val storagePermissionStatus =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
        if (storagePermissionStatus == PackageManager.PERMISSION_GRANTED)
            when (mediaContentType) {
                AUDIO_CONTENT_TYPE -> chooseAudioLauncher.launch("audio/*")
                VIDEO_CONTENT_TYPE -> chooseVideoLauncher.launch("video/*")
            }
        else
            checkStoragePermissionLauncher(mediaContentType)
    }

    private fun checkStoragePermissionLauncher(mediaContentType: Int) {
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val granted = permissions[Manifest.permission.READ_EXTERNAL_STORAGE] == true
            when {
                granted -> {
                    pickMediaContent(mediaContentType)
                }
                !shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE) -> showPermissionDeniedDialog()
                else -> showRationaleDialog(mediaContentType)
            }
        }
    }

    private val chooseVideoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it == null) return@registerForActivityResult
            playerViewModel.updateVideoUri(it)
        }

    private val chooseAudioLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it == null) return@registerForActivityResult
            playerViewModel.asyncSaveWithNewAudio(this@PlayerActivity, it)
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

    private fun showRationaleDialog(mediaContentType: Int) {
        AlertDialog.Builder(this)
            .setTitle(R.string.permission_required)
            .setMessage(R.string.storage_permission_rationale)
            .setPositiveButton(R.string.allow) { _, _ ->
                checkStoragePermissionLauncher(mediaContentType)
            }
            .setNegativeButton(R.string.cancel) { _, _ -> }
            .show()
    }

    private fun turnOnLoadingScreen() {
        binding.playerView.visibility = View.GONE
        binding.controlsRoot.visibility = View.GONE
        binding.loadingText.visibility = View.VISIBLE
//        binding.controlsRoot.visibility = View.GONE
        playerViewModel.startAutoPlay = player.playWhenReady
        player.playWhenReady = false
        Log.d("debug", "turnOnLoadingScreen()")
    }

    private fun turnOffLoadingScreen() {
        binding.playerView.visibility = View.VISIBLE
        binding.loadingText.visibility = View.GONE
        player.playWhenReady = playerViewModel.startAutoPlay
        Log.d("debug", "turnOffLoadingScreen()")
    }

    private fun haveDifferentExtras(intent1: Intent?, intent2: Intent?): Boolean =
        intent1?.getParcelableExtra<Uri>(EXTRA_VIDEO_URI)!! != intent2?.getParcelableExtra<Uri>(EXTRA_VIDEO_URI)!!
}
