package com.quick.voice.recorder.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.quick.voice.recorder.R
import com.quick.voice.recorder.VoiceRecorderApplication
import com.quick.voice.recorder.ui.PlayerActivity
import com.quick.voice.recorder.utils.FileUtils
import java.io.IOException
import java.util.Timer
import java.util.TimerTask

class PlayerService : Service() {

    private val binder = PlayerBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var currentFilePath: String? = null
    private var playerCallback: PlayerCallback? = null
    private var progressTimer: Timer? = null
    private var isPrepared = false

    interface PlayerCallback {
        fun onPlaybackCompleted()
        fun onPlaybackError(errorMessage: String)
        fun onPlaybackProgress(currentPosition: Int, duration: Int)
        fun onPlaybackStateChanged(isPlaying: Boolean)
    }

    inner class PlayerBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }

    override fun onBind(intent: Intent): IBinder = binder

    fun setPlayerCallback(callback: PlayerCallback) {
        this.playerCallback = callback
        // Notify current state to new callback
        callback.onPlaybackStateChanged(isPlaying())
    }

    fun removePlayerCallback() {
        this.playerCallback = null
    }

    fun initializePlayer(filePath: String) {
        currentFilePath = filePath
        prepareMediaPlayer()
    }

    private fun prepareMediaPlayer() {
        cleanupMediaPlayer()

        try {
            mediaPlayer = MediaPlayer().apply {
                setDataSource(currentFilePath)
                setOnPreparedListener {
                    isPrepared = true
                    playerCallback?.onPlaybackStateChanged(isPlaying())
                    startProgressUpdates()
                }
                setOnCompletionListener {
                    stopProgressUpdates()
                    playerCallback?.onPlaybackCompleted()
                    playerCallback?.onPlaybackStateChanged(false)
                    updateNotification(false)
                }
                setOnErrorListener { _, what, extra ->
                    isPrepared = false
                    stopProgressUpdates()
                    val errorMessage = when (what) {
                        MediaPlayer.MEDIA_ERROR_UNKNOWN -> "Unknown media error"
                        MediaPlayer.MEDIA_ERROR_SERVER_DIED -> "Media server died"
                        else -> "Error: $what, $extra"
                    }
                    playerCallback?.onPlaybackError(errorMessage)
                    playerCallback?.onPlaybackStateChanged(false)
                    false
                }
                setOnSeekCompleteListener {
                    // Seek completed
                }
                prepareAsync()
            }
        } catch (e: IOException) {
            handlePlaybackError("File not found or cannot be opened")
        } catch (e: IllegalStateException) {
            handlePlaybackError("Media player in illegal state")
        } catch (e: Exception) {
            handlePlaybackError("Failed to initialize media player: ${e.message}")
        }
    }

    fun playAudio(): Boolean {
        return if (isPrepared && mediaPlayer != null) {
            try {
                mediaPlayer?.start()
                startProgressUpdates()
                startForeground(NOTIFICATION_ID, createNotification(true))
                playerCallback?.onPlaybackStateChanged(true)
                true
            } catch (e: IllegalStateException) {
                handlePlaybackError("Media player not in valid state")
                false
            }
        } else {
            // If not prepared, try to prepare again
            currentFilePath?.let { filePath ->
                initializePlayer(filePath)
            }
            false
        }
    }

    fun pauseAudio() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        stopProgressUpdates()
        updateNotification(false)
        playerCallback?.onPlaybackStateChanged(false)
    }

    fun stopAudio() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            seekTo(0)
        }
        stopProgressUpdates()
        stopForeground(STOP_FOREGROUND_REMOVE)
        playerCallback?.onPlaybackStateChanged(false)
    }

    fun seekTo(position: Int) {
        if (isPrepared) {
            mediaPlayer?.seekTo(position.coerceAtLeast(0))
        }
    }

    fun getCurrentPosition(): Int = if (isPrepared) mediaPlayer?.currentPosition ?: 0 else 0

    fun getDuration(): Int = if (isPrepared) mediaPlayer?.duration ?: 0 else 0

    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false

    fun isInitialized(): Boolean = isPrepared

    fun skipForward(milliseconds: Int = SKIP_INTERVAL_MS) {
        mediaPlayer?.let { player ->
            if (isPrepared) {
                val newPosition = (player.currentPosition + milliseconds).coerceAtMost(player.duration)
                player.seekTo(newPosition)
                playerCallback?.onPlaybackProgress(newPosition, player.duration)
            }
        }
    }

    fun skipBackward(milliseconds: Int = SKIP_INTERVAL_MS) {
        mediaPlayer?.let { player ->
            if (isPrepared) {
                val newPosition = (player.currentPosition - milliseconds).coerceAtLeast(0)
                player.seekTo(newPosition)
                playerCallback?.onPlaybackProgress(newPosition, player.duration)
            }
        }
    }

    private fun startProgressUpdates() {
        stopProgressUpdates()
        progressTimer = Timer().apply {
            scheduleAtFixedRate(object : TimerTask() {
                override fun run() {
                    mediaPlayer?.let { player ->
                        if (isPrepared && player.isPlaying) {
                            val currentPos = player.currentPosition
                            val duration = player.duration
                            playerCallback?.onPlaybackProgress(currentPos, duration)
                        }
                    }
                }
            }, 0, PROGRESS_UPDATE_INTERVAL)
        }
    }

    private fun stopProgressUpdates() {
        progressTimer?.cancel()
        progressTimer = null
    }

    private fun createNotification(isPlaying: Boolean): Notification {
        createNotificationChannel()

        val intent = Intent(this, PlayerActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseAction = NotificationCompat.Action.Builder(
            if (isPlaying) R.drawable.ic_pause else R.drawable.ic_play,
            if (isPlaying) getString(R.string.pause) else getString(R.string.play),
            createActionPendingIntent(if (isPlaying) ACTION_PAUSE else ACTION_PLAY)
        ).build()

        val stopAction = NotificationCompat.Action.Builder(
            R.drawable.ic_stop,
            getString(R.string.stop),
            createActionPendingIntent(ACTION_STOP)
        ).build()

        val rewindAction = NotificationCompat.Action.Builder(
            R.drawable.ic_rewind,
            getString(R.string.rewind),
            createActionPendingIntent(ACTION_REWIND)
        ).build()

        val forwardAction = NotificationCompat.Action.Builder(
            R.drawable.ic_forward,
            getString(R.string.forward),
            createActionPendingIntent(ACTION_FORWARD)
        ).build()

        return NotificationCompat.Builder(this, VoiceRecorderApplication.PLAYER_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (isPlaying) getString(R.string.playing_audio) else getString(R.string.audio_paused))
            .setSmallIcon(R.drawable.ic_music_note)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setStyle(androidx.media.app.NotificationCompat.MediaStyle()
                .setShowActionsInCompactView(0, 1, 2)
                .setMediaSession(null)) // You can set media session here if using MediaSession
            .addAction(rewindAction)
            .addAction(playPauseAction)
            .addAction(forwardAction)
            .addAction(stopAction)
            .build()
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, PlayerService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VoiceRecorderApplication.PLAYER_CHANNEL_ID,
                getString(R.string.player_channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.player_channel_description)
                setShowBadge(false)
                setSound(null, null)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> playAudio()
            ACTION_PAUSE -> pauseAudio()
            ACTION_STOP -> stopAudio()
            ACTION_REWIND -> skipBackward()
            ACTION_FORWARD -> skipForward()
        }
        return START_STICKY
    }

    private fun updateNotification(isPlaying: Boolean) {
        val notification = createNotification(isPlaying)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    private fun handlePlaybackError(errorMessage: String) {
        playerCallback?.onPlaybackError(errorMessage)
        cleanupMediaPlayer()
    }

    private fun cleanupMediaPlayer() {
        stopProgressUpdates()
        mediaPlayer?.apply {
            reset()
            release()
        }
        mediaPlayer = null
        isPrepared = false
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    override fun onDestroy() {
        super.onDestroy()
        cleanupMediaPlayer()
    }

    companion object {
        private const val NOTIFICATION_ID = 1002
        private const val PROGRESS_UPDATE_INTERVAL = 1000L // 1 second
        private const val SKIP_INTERVAL_MS = 5000 // 5 seconds

        // Notification actions
        const val ACTION_PLAY = "play_audio"
        const val ACTION_PAUSE = "pause_audio"
        const val ACTION_STOP = "stop_audio"
        const val ACTION_REWIND = "rewind_audio"
        const val ACTION_FORWARD = "forward_audio"
    }
}