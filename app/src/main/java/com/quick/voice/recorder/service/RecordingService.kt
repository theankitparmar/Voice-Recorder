package com.quick.voice.recorder.service

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
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
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class RecordingService : Service() {

    private val binder = RecordingBinder()
    private var mediaRecorder: MediaRecorder? = null
    private var isRecording = false
    private var isPaused = false
    private var outputFile: String? = null
    private var startTime: Long = 0
    private var pausedDuration: Long = 0
    private var lastPauseTime: Long = 0

    inner class RecordingBinder : Binder() {
        fun getService(): RecordingService = this@RecordingService
    }

    override fun onBind(intent: Intent): IBinder = binder

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PAUSE -> pauseRecording()
            ACTION_RESUME -> resumeRecording()
            ACTION_STOP -> stopRecording()
            else -> {
                intent?.getStringExtra(EXTRA_OUTPUT_FILE)?.let { filePath ->
                    if (!isRecording) {
                        startRecording(filePath)
                    }
                }
            }
        }
        return START_STICKY
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                VoiceRecorderApplication.RECORDING_CHANNEL_ID,
                "Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows ongoing voice recording"
                setShowBadge(false)
                setSound(null, null)
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(channel)
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
            true
        }

        return hasRecordAudio && hasForegroundServiceMicrophone
    }

    fun startRecording(outputPath: String): Boolean {
        if (!hasRequiredPermissions()) {
            return false
        }

        outputFile = outputPath

        try {
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
                setAudioEncodingBitRate(128000)
                setAudioSamplingRate(44100)
                setOutputFile(outputPath)

                prepare()
                start()

                isRecording = true
                isPaused = false
                startTime = System.currentTimeMillis()
                pausedDuration = 0

                startForeground(NOTIFICATION_ID, createNotification())
                return true
            }
        } catch (e: IOException) {
            e.printStackTrace()
            cleanupMediaRecorder()
        } catch (e: IllegalStateException) {
            e.printStackTrace()
            cleanupMediaRecorder()
        }
        return false
    }

    fun pauseRecording() {
        if (isRecording && !isPaused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.pause()
                isPaused = true
                lastPauseTime = System.currentTimeMillis()
                updateNotification()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    fun resumeRecording() {
        if (isRecording && isPaused && Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            try {
                mediaRecorder?.resume()
                isPaused = false
                pausedDuration += System.currentTimeMillis() - lastPauseTime
                updateNotification()
            } catch (e: IllegalStateException) {
                e.printStackTrace()
            }
        }
    }

    fun stopRecording(): String? {
        return try {
            if (isRecording) {
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                cleanupMediaRecorder()
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
                mediaRecorder?.apply {
                    stop()
                    release()
                }
                cleanupMediaRecorder()

                // Delete the file
                outputFile?.let { File(it).delete() }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun cleanupMediaRecorder() {
        mediaRecorder = null
        isRecording = false
        isPaused = false
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    fun getCurrentDuration(): Long {
        return if (isRecording) {
            if (isPaused) {
                (lastPauseTime - startTime) - pausedDuration
            } else {
                (System.currentTimeMillis() - startTime) - pausedDuration
            }
        } else 0
    }

    fun isCurrentlyRecording(): Boolean = isRecording
    fun isCurrentlyPaused(): Boolean = isPaused

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notificationBuilder = NotificationCompat.Builder(this, VoiceRecorderApplication.RECORDING_CHANNEL_ID)
            .setContentTitle(getString(R.string.app_name))
            .setContentText(if (isPaused) "Recording paused" else "Recording in progress...")
            .setSmallIcon(R.drawable.ic_mic)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)

        // Add action buttons
        val pauseResumeAction = if (isPaused) {
            NotificationCompat.Action.Builder(
                R.drawable.ic_play,
                "Resume",
                createActionPendingIntent(ACTION_RESUME)
            ).build()
        } else {
            NotificationCompat.Action.Builder(
                R.drawable.ic_pause,
                "Pause",
                createActionPendingIntent(ACTION_PAUSE)
            ).build()
        }

        val stopAction = NotificationCompat.Action.Builder(
            R.drawable.ic_launcher_foreground,
            "Stop",
            createActionPendingIntent(ACTION_STOP)
        ).build()

        notificationBuilder.addAction(pauseResumeAction)
        notificationBuilder.addAction(stopAction)

        return notificationBuilder.build()
    }

    private fun createActionPendingIntent(action: String): PendingIntent {
        val intent = Intent(this, RecordingService::class.java).apply {
            this.action = action
        }
        return PendingIntent.getService(
            this,
            action.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun updateNotification() {
        val notification = createNotification()
        val notificationManager = getSystemService(NOTIFICATION_SERVICE) as android.app.NotificationManager
        notificationManager.notify(NOTIFICATION_ID, notification)
    }

    companion object {
        const val NOTIFICATION_ID = 1001
        const val EXTRA_OUTPUT_FILE = "output_file"

        // Notification actions
        const val ACTION_PAUSE = "pause_recording"
        const val ACTION_RESUME = "resume_recording"
        const val ACTION_STOP = "stop_recording"
    }
}