// Update RecordingService.kt
package com.quick.voice.recorder.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.quick.voice.recorder.R
import com.quick.voice.recorder.VoiceRecorderApplication
import com.quick.voice.recorder.ui.MainActivity
import java.io.File
import java.io.IOException

class RecordingService : Service() {

    private val binder = RecordingBinder()
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var isPaused = false
    private var outputFile: String? = null
    private var startTime: Long = 0
    private var pausedDuration: Long = 0

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()

        // Check permissions before starting foreground service
        if (hasRequiredPermissions()) {
            startForeground(NOTIFICATION_ID, createNotification())
        } else {
            stopSelf()
        }
    }

    private fun hasRequiredPermissions(): Boolean {
        val hasRecordAudio = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        val hasForegroundServiceMicrophone = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.FOREGROUND_SERVICE_MICROPHONE
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required for older versions
        }

        return hasRecordAudio && hasForegroundServiceMicrophone
    }

    fun startRecording(outputPath: String): Boolean {
        if (!hasRequiredPermissions()) {
            return false
        }

        outputFile = outputPath

        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            @Suppress("DEPRECATION")
            MediaRecorder()
        }

        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(outputPath)

            try {
                prepare()
                start()
                isRecording = true
                isPaused = false
                startTime = System.currentTimeMillis()
                pausedDuration = 0
                updateNotification()
                return true // Explicitly return true on success
            } catch (e: IOException) {
                e.printStackTrace()
                return false // Explicitly return false on failure
            }
        }
        return false // Return false if mediaRecorder is null or apply block doesn't execute
    }


    fun pauseRecording() {
        if (isRecording && !isPaused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.pause()
            isPaused = true
            pausedDuration += System.currentTimeMillis() - startTime
            updateNotification()
        }
    }

    fun resumeRecording() {
        if (isRecording && isPaused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            mediaRecorder?.resume()
            isPaused = false
            startTime = System.currentTimeMillis()
            updateNotification()
        }
    }

    fun stopRecording(): String? {
        return try {
            if (isRecording) {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
                isPaused = false

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()

                outputFile
            } else null
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun discardRecording() {
        try {
            if (isRecording) {
                mediaRecorder?.stop()
                mediaRecorder?.release()
                mediaRecorder = null
                isRecording = false
                isPaused = false

                // Delete the file
                outputFile?.let { File(it).delete() }

                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    fun getCurrentDuration(): Long {
        return if (isRecording) {
            if (isPaused) {
                pausedDuration
            } else {
                pausedDuration + (System.currentTimeMillis() - startTime)
            }
        } else 0
    }

    fun isCurrentlyRecording(): Boolean = isRecording
    fun isCurrentlyPaused(): Boolean = isPaused

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, VoiceRecorderApplication.RECORDING_CHANNEL_ID)
            .setContentTitle("Voice Recorder")
            .setContentText("Recording...")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}