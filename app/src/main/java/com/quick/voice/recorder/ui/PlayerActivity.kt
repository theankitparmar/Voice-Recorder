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
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.quick.voice.recorder.R
import com.quick.voice.recorder.data.database.Recording
import com.quick.voice.recorder.databinding.ActivityPlayerBinding
import com.quick.voice.recorder.service.PlayerService
import okhttp3.internal.concurrent.formatDuration

class PlayerActivity : AppCompatActivity(), PlayerService.PlayerCallback {

    private lateinit var binding: ActivityPlayerBinding
    private var playerService: PlayerService? = null
    private var isServiceBound = false
    private var recording: Recording? = null
    private val handler = Handler(Looper.getMainLooper())
    private var progressRunnable: Runnable? = null

    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val binder = service as PlayerService.PlayerBinder
            playerService = binder.getService()
            playerService?.setPlayerCallback(this@PlayerActivity)
            isServiceBound = true
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

        recording = intent.getParcelableExtra("recording")

        setupUI()
        bindPlayerService()
    }

    private fun setupUI() {
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = recording?.fileName ?: "Player"

        recording?.let { rec ->
            binding.tvFileName.text = rec.fileName
            binding.tvDuration.text = formatDuration(rec.duration)
            binding.seekBar.max = rec.duration.toInt()
        }

        binding.apply {
            btnPlayPause.setOnClickListener { togglePlayPause() }
            btnRewind.setOnClickListener { rewind() }
            btnForward.setOnClickListener { forward() }

            seekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) {
                        binding.tvCurrentTime.text = formatDuration(progress.toLong())
                    }
                }

                override fun onStartTrackingTouch(seekBar: SeekBar?) {}

                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    seekBar?.progress?.let { progress ->
                        playerService?.seekTo(progress)
                    }
                }
            })
        }

        updatePlayerUI()
    }

    private fun bindPlayerService() {
        val intent = Intent(this, PlayerService::class.java)
        bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE)
    }

    private fun togglePlayPause() {
        playerService?.let { service ->
            if (service.isPlaying()) {
                service.pauseAudio()
                stopProgressUpdates()
            } else {
                recording?.let { rec ->
                    service.playAudio(rec.filePath)
                    startProgressUpdates()
                }
            }
            updatePlayerUI()
        }
    }

    private fun rewind() {
        playerService?.skipBackward()
    }

    private fun forward() {
        playerService?.skipForward()
    }

    private fun startProgressUpdates() {
        progressRunnable = object : Runnable {
            override fun run() {
                playerService?.let { service ->
                    val currentPosition = service.getCurrentPosition()
                    val duration = service.getDuration()

                    binding.seekBar.progress = currentPosition
                    binding.tvCurrentTime.text = formatDuration(currentPosition.toLong())

                    if (service.isPlaying()) {
                        handler.postDelayed(this, 100)
                    }
                }
            }
        }
        handler.post(progressRunnable!!)
    }

    private fun stopProgressUpdates() {
        progressRunnable?.let { handler.removeCallbacks(it) }
        progressRunnable = null
    }

    private fun updatePlayerUI() {
        val isPlaying = playerService?.isPlaying() ?: false

        binding.btnPlayPause.setIconResource(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play
        )

        if (isPlaying) {
            startProgressUpdates()
        } else {
            stopProgressUpdates()
        }
    }

    override fun onPlaybackCompleted() {
        runOnUiThread {
            binding.seekBar.progress = 0
            binding.tvCurrentTime.text = "00:00"
            updatePlayerUI()
        }
    }

    override fun onPlaybackError() {
        runOnUiThread {
            // Handle playback error
            updatePlayerUI()
        }
    }

    override fun onPlaybackProgress(currentPosition: Int, duration: Int) {
        runOnUiThread {
            binding.seekBar.progress = currentPosition
            binding.tvCurrentTime.text = formatDuration(currentPosition.toLong())
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressed()
        return true
    }

    override fun onDestroy() {
        super.onDestroy()
        stopProgressUpdates()
        if (isServiceBound) {
            unbindService(serviceConnection)
        }
    }
}
