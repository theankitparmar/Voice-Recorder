package com.quick.voice.recorder.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.media.MediaRecorder
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.quick.voice.recorder.R
import com.quick.voice.recorder.ui.MainActivity
import java.io.File
import java.io.IOException

class RecordingService : Service() {

    private val binder = RecordingBinder()
    private var mediaRecorder: MediaRecorder? = null
    private var outputFile: File? = null
    private var isRecording = false
    private var isPaused = false
    private var recordingStartTime: Long = 0
    private var pausedDuration: Long = 0 // Time accumulated during pauses

    private val NOTIFICATION_ID = 101
    private val NOTIFICATION_CHANNEL_ID = "voice_recorder_channel"

    companion object {
        const val EXTRA_OUTPUT_FILE = "extra_output_file"
    }

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent?): IBinder = binder

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val filePath = intent?.getStringExtra(EXTRA_OUTPUT_FILE)
        if (filePath != null && !isRecording) {
            startNewRecording(filePath)
        } else if (isRecording) {
            // Service already running, just ensure it's in foreground
            startForegroundNotification()
        }
        return START_STICKY
    }

    private fun startNewRecording(filePath: String) {
        outputFile = File(filePath)
        mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            MediaRecorder(this)
        } else {
            MediaRecorder()
        }

        mediaRecorder?.apply {
            setAudioSource(MediaRecorder.AudioSource.MIC)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setAudioChannels(1)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(128000)
            setOutputFile(outputFile?.absolutePath)
            try {
                prepare()
                start()
                isRecording = true
                isPaused = false
                recordingStartTime = System.currentTimeMillis()
                pausedDuration = 0
                startForegroundNotification()
                Log.d("RecordingService", "Recording started: ${outputFile?.absolutePath}")
            } catch (e: IOException) {
                Log.e("RecordingService", "Failed to start recording: ${e.message}", e)
                isRecording = false
                stopSelf() // Stop service if recording fails
            } catch (e: IllegalStateException) {
                Log.e("RecordingService", "Illegal state during recording setup: ${e.message}", e)
                isRecording = false
                stopSelf() // Stop service if recording fails
            }
        }
    }

    fun stopRecording(): String? {
        if (isRecording) {
            mediaRecorder?.apply {
                try {
                    stop()
                    release()
                    Log.d("RecordingService", "Recording stopped: ${outputFile?.absolutePath}")
                    return outputFile?.absolutePath
                } catch (e: RuntimeException) {
                    Log.e("RecordingService", "Error stopping recording: ${e.message}", e)
                    // If stop fails (e.g., no valid audio recorded), delete the file
                    outputFile?.delete()
                    return null
                } finally {
                    mediaRecorder = null
                    isRecording = false
                    isPaused = false
                    stopForeground(STOP_FOREGROUND_REMOVE)
                }
            }
        }
        return null
    }

    fun pauseRecording() {
        if (isRecording && !isPaused) {
            mediaRecorder?.pause()
            isPaused = true
            // Accumulate duration that has passed before pausing
            pausedDuration += (System.currentTimeMillis() - recordingStartTime)
            Log.d("RecordingService", "Recording paused.")
            startForegroundNotification() // Update notification
        }
    }

    fun resumeRecording() {
        if (isRecording && isPaused) {
            mediaRecorder?.resume()
            isPaused = false
            // Reset start time to accurately calculate duration from now
            recordingStartTime = System.currentTimeMillis()
            Log.d("RecordingService", "Recording resumed.")
            startForegroundNotification() // Update notification
        }
    }

    fun discardRecording() {
        if (isRecording) {
            mediaRecorder?.apply {
                stop() // Even if not saving, need to stop recorder gracefully
                release()
            }
            outputFile?.delete()
            Log.d("RecordingService", "Recording discarded.")
        }
        mediaRecorder = null
        isRecording = false
        isPaused = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun isCurrentlyRecording(): Boolean = isRecording
    fun isCurrentlyPaused(): Boolean = isPaused

    fun getCurrentDuration(): Long {
        return if (isRecording) {
            if (isPaused) {
                pausedDuration // Return accumulated time if paused
            } else {
                pausedDuration + (System.currentTimeMillis() - recordingStartTime)
            }
        } else {
            0L
        }
    }

    /**
     * Returns the current amplitude of the audio being recorded.
     * @return The maximum amplitude in the current frame, or 0 if not recording or an error occurs.
     */
    fun getCurrentAmplitude(): Int {
        return if (isRecording && !isPaused && mediaRecorder != null) {
            try {
                mediaRecorder?.maxAmplitude ?: 0
            } catch (e: IllegalStateException) {
                Log.e("RecordingService", "Error getting max amplitude: ${e.message}", e)
                0
            }
        } else {
            0
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "Voice Recorder Service Channel",
                NotificationManager.IMPORTANCE_LOW // Use LOW for ongoing background task
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun startForegroundNotification() {
        createNotificationChannel()

        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            PendingIntent.FLAG_IMMUTABLE // Use FLAG_IMMUTABLE for security
        )

        val notificationTitle = if (isPaused) "Recording Paused" else "Recording in Progress"
        val notificationText = if (isPaused) "Tap to resume" else "Tap to manage recording"

        val notification = NotificationCompat.Builder(this, NOTIFICATION_CHANNEL_ID)
            .setContentTitle(notificationTitle)
            .setContentText(notificationText)
            .setSmallIcon(R.drawable.ic_mic) // Use an appropriate icon
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()

        startForeground(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        if (isRecording) { // If service is destroyed while recording, stop and delete
            mediaRecorder?.apply {
                try {
                    stop()
                } catch (e: RuntimeException) {
                    Log.e("RecordingService", "Error stopping recorder in onDestroy: ${e.message}")
                }
                release()
            }
            outputFile?.delete()
            Log.d("RecordingService", "Recording aborted and file deleted in onDestroy.")
        }
        mediaRecorder = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        Log.d("RecordingService", "RecordingService destroyed.")
    }
}