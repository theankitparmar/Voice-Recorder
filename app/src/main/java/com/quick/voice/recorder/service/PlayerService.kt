// service/PlayerService.kt
package com.quick.voice.recorder.service

import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaPlayer
import android.os.Binder
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.quick.voice.recorder.R
import com.quick.voice.recorder.VoiceRecorderApplication
import com.quick.voice.recorder.ui.PlayerActivity
import java.io.IOException
import kotlin.jvm.java

class PlayerService : Service() {
    
    private val binder = PlayerBinder()
    private var mediaPlayer: MediaPlayer? = null
    private var currentFilePath: String? = null
    private var playerCallback: PlayerCallback? = null
    
    interface PlayerCallback {
        fun onPlaybackCompleted()
        fun onPlaybackError()
        fun onPlaybackProgress(currentPosition: Int, duration: Int)
    }
    
    inner class PlayerBinder : Binder() {
        fun getService(): PlayerService = this@PlayerService
    }
    
    override fun onBind(intent: Intent): IBinder = binder
    
    fun setPlayerCallback(callback: PlayerCallback) {
        this.playerCallback = callback
    }
    
    fun playAudio(filePath: String): Boolean {
        return try {
            if (currentFilePath != filePath) {
                stopAudio()
                currentFilePath = filePath
                
                mediaPlayer = MediaPlayer().apply {
                    setDataSource(filePath)
                    prepareAsync()
                    setOnPreparedListener {
                        start()
                        startForeground(NOTIFICATION_ID, createNotification(true))
                    }
                    setOnCompletionListener {
                        playerCallback?.onPlaybackCompleted()
                        stopForeground(STOP_FOREGROUND_REMOVE)
                    }
                    setOnErrorListener { _, _, _ ->
                        playerCallback?.onPlaybackError()
                        false
                    }
                }
            } else {
                if (mediaPlayer?.isPlaying == false) {
                    mediaPlayer?.start()
                    startForeground(NOTIFICATION_ID, createNotification(true))
                }
            }
            true
        } catch (e: IOException) {
            e.printStackTrace()
            playerCallback?.onPlaybackError()
            false
        }
    }
    
    fun pauseAudio() {
        mediaPlayer?.takeIf { it.isPlaying }?.pause()
        updateNotification(false)
    }
    
    fun stopAudio() {
        mediaPlayer?.apply {
            if (isPlaying) stop()
            release()
        }
        mediaPlayer = null
        currentFilePath = null
        stopForeground(STOP_FOREGROUND_REMOVE)
    }
    
    fun seekTo(position: Int) {
        mediaPlayer?.seekTo(position)
    }
    
    fun getCurrentPosition(): Int = mediaPlayer?.currentPosition ?: 0
    
    fun getDuration(): Int = mediaPlayer?.duration ?: 0
    
    fun isPlaying(): Boolean = mediaPlayer?.isPlaying ?: false
    
    fun skipForward(milliseconds: Int = 5000) {
        mediaPlayer?.let { player ->
            val newPosition = (player.currentPosition + milliseconds).coerceAtMost(player.duration)
            player.seekTo(newPosition)
        }
    }
    
    fun skipBackward(milliseconds: Int = 5000) {
        mediaPlayer?.let { player ->
            val newPosition = (player.currentPosition - milliseconds).coerceAtLeast(0)
            player.seekTo(newPosition)
        }
    }
    
    private fun createNotification(isPlaying: Boolean): Notification {
        val intent = Intent(this, PlayerActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        
        return NotificationCompat.Builder(this, VoiceRecorderApplication.PLAYER_CHANNEL_ID)
            .setContentTitle("Voice Recorder")
            .setContentText(if (isPlaying) "Playing audio..." else "Audio paused")
            .setSmallIcon(if (isPlaying) R.drawable.ic_play else R.drawable.ic_pause)
            .setContentIntent(pendingIntent)
            .setOngoing(isPlaying)
            .build()
    }
    
    private fun updateNotification(isPlaying: Boolean) {
        val notification = createNotification(isPlaying)
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    override fun onDestroy() {
        super.onDestroy()
        stopAudio()
    }
    
    companion object {
        private const val NOTIFICATION_ID = 1002
    }
}
