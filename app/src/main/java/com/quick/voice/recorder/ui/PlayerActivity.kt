package com.quick.voice.recorder.ui

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.widget.SeekBar
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.quick.voice.recorder.R
import com.quick.voice.recorder.VoiceRecorderApplication
import com.quick.voice.recorder.data.database.Recording
import com.quick.voice.recorder.databinding.ActivityPlayerBinding
import com.quick.voice.recorder.service.PlayerService
import com.quick.voice.recorder.ui.adapter.formattedCreatedDate
import com.quick.voice.recorder.utils.FileUtils.formatDuration
import com.quick.voice.recorder.viewmodel.PlayerViewModel
import com.quick.voice.recorder.viewmodel.PlayerViewModelFactory
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

class PlayerActivity : AppCompatActivity(), PlayerService.PlayerCallback {

    private lateinit var binding: ActivityPlayerBinding
    private var playerService: PlayerService? = null
    private var isServiceBound = false
    private var recording: Recording? = null

    private val viewModel: PlayerViewModel by viewModels {
        PlayerViewModelFactory((application as VoiceRecorderApplication).repository)
    }

    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null
    private var isSeeking = false

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlayerService.PlayerBinder
            playerService = binder.getService()
            playerService?.setPlayerCallback(this@PlayerActivity)
            isServiceBound = true
            initializePlayer()
            updatePlayerUI()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            playerService = null
            isServiceBound = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityPlayerBinding.inflate(layoutInflater)
        setContentView(binding.root)

        recording = intent.getParcelableExtra(EXTRA_RECORDING)
        if (recording == null) {
            finish()
            return
        }

        setupUI()
        bindPlayerService()
        observePlaybackState()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = recording?.fileName ?: getString(R.string.audio_player)

        recording?.let { rec ->
            binding.apply {
                tvFileName.text = rec.fileName
                tvDuration.text = formatDuration(rec.duration)
                tvFileSize.text = getString(R.string.file_size_format, rec.fileSize.toString())
                tvCreatedDate.text = rec.formattedCreatedDate

                seekBar.max = rec.duration.toInt()

                btnPlayPause.setOnClickListener { togglePlayPause() }
                btnRewind.setOnClickListener { rewind() }
                btnForward.setOnClickListener { forward() }
                btnStop.setOnClickListener { stopPlayback() }

                seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                    override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                        if (fromUser) {
                            binding.tvCurrentTime.text = formatDuration(progress.toLong())
                        }
                    }

                    override fun onStartTrackingTouch(seekBar: SeekBar?) {
                        isSeeking = true
                        stopProgressUpdates()
                    }

                    override fun onStopTrackingTouch(seekBar: SeekBar?) {
                        isSeeking = false
                        seekBar?.progress?.let { progress ->
                            playerService?.seekTo(progress)
                            startProgressUpdates()
                        }
                    }
                })
            }
        }

        updatePlayerUI()
    }

    private fun bindPlayerService() {
        val intent = Intent(this, PlayerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun initializePlayer() {
        recording?.let { rec ->
            playerService?.initializePlayer(rec.filePath)
        }
    }

    private fun togglePlayPause() {
        playerService?.let { service ->
            if (service.isPlaying()) {
                service.pauseAudio()
                stopProgressUpdates()
            } else {
                service.playAudio()
                startProgressUpdates()
            }
            updatePlayerUI()
        }
    }

    private fun rewind() {
        playerService?.skipBackward()
        updateCurrentPosition()
    }

    private fun forward() {
        playerService?.skipForward()
        updateCurrentPosition()
    }

    private fun stopPlayback() {
        playerService?.stopAudio()
        stopProgressUpdates()
        resetSeekBar()
        updatePlayerUI()
    }

    private fun startProgressUpdates() {
        stopProgressUpdates() // Ensure no existing updates
        progressRunnable = object : Runnable {
            override fun run() {
                if (!isSeeking) {
                    updateCurrentPosition()
                }
                handler.postDelayed(this, PROGRESS_UPDATE_INTERVAL)
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun updateCurrentPosition() {
        playerService?.let { service ->
            val currentPosition = service.getCurrentPosition()
            val duration = service.getDuration()

            if (duration > 0) {
                binding.seekBar.progress = currentPosition
                binding.seekBar.max = duration
                binding.tvCurrentTime.text = formatDuration(currentPosition.toLong())
                binding.tvDuration.text = formatDuration(duration.toLong())
            }
        }
    }

    private fun resetSeekBar() {
        binding.seekBar.progress = 0
        binding.tvCurrentTime.text = formatDuration(0)
    }

    private fun updatePlayerUI() {
        val isPlaying = playerService?.isPlaying() ?: false
        val isInitialized = playerService?.isInitialized() ?: false

        binding.apply {
            btnPlayPause.setIconResource(
                if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
            )

            btnPlayPause.isEnabled = isInitialized
            btnRewind.isEnabled = isInitialized
            btnForward.isEnabled = isInitialized
            btnStop.isEnabled = isInitialized
            seekBar.isEnabled = isInitialized

            // Update play/pause button content description for accessibility
            btnPlayPause.contentDescription = if (isPlaying) {
                getString(R.string.pause_audio)
            } else {
                getString(R.string.play_audio)
            }
        }

        if (isPlaying) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
    }

    private fun observePlaybackState() {
        lifecycleScope.launch {
            viewModel.playbackState.collectLatest { state ->
                // Handle playback state changes if needed
                when (state) {
                    is PlayerViewModel.PlaybackState.Playing -> {
                        startProgressUpdates()
                        updatePlayerUI()
                    }
                    is PlayerViewModel.PlaybackState.Paused -> {
                        stopProgressUpdates()
                        updatePlayerUI()
                    }
                    is PlayerViewModel.PlaybackState.Stopped -> {
                        stopProgressUpdates()
                        resetSeekBar()
                        updatePlayerUI()
                    }
                    else -> {}
                }
            }
        }
    }

    // PlayerService Callbacks
    override fun onPlaybackCompleted() {
        runOnUiThread {
            binding.seekBar.progress = binding.seekBar.max
            updatePlayerUI()

            // Auto-rewind to start after completion
            handler.postDelayed({
                resetSeekBar()
                updatePlayerUI()
            }, AUTO_REWIND_DELAY)
        }
    }

    override fun onPlaybackError(errorMessage: String) {
        runOnUiThread {
            stopProgressUpdates()
            updatePlayerUI()

            // Show error message
            com.google.android.material.snackbar.Snackbar.make(
                binding.root,
                getString(R.string.playback_error, errorMessage),
                com.google.android.material.snackbar.Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onPlaybackProgress(currentPosition: Int, duration: Int) {
        // This callback is now handled by our progress runnable
        // Keeping it for service-initiated updates if needed
    }

    override fun onPlaybackStateChanged(isPlaying: Boolean) {
        runOnUiThread {
            updatePlayerUI()
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onPause() {
        super.onPause()
        // Don't stop playback when activity is paused
        // This allows audio to continue playing in background
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdates()
        if (isServiceBound) {
            playerService?.removePlayerCallback()
            unbindService(serviceConnection)
        }
    }

    companion object {
        private const val EXTRA_RECORDING = "recording"
        private const val PROGRESS_UPDATE_INTERVAL = 100L // milliseconds
        private const val AUTO_REWIND_DELAY = 1000L // milliseconds

        fun createIntent(context: Context, recording: Recording): Intent {
            return Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_RECORDING, recording)
            }
        }
    }
}