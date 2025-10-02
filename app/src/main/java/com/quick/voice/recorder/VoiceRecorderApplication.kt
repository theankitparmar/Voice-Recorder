package com.quick.voice.recorder

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.room.Room
import com.quick.voice.recorder.data.database.RecordingDatabase
import com.quick.voice.recorder.data.repository.RecordingRepository
import kotlin.jvm.java

class VoiceRecorderApplication : Application() {

    val database by lazy {
        Room.databaseBuilder(
            this,
            RecordingDatabase::class.java,
            "recording_database"
        ).build()
    }

    val repository by lazy { RecordingRepository(database.recordingDao()) }

    companion object {
        const val RECORDING_CHANNEL_ID = "recording_channel"
        const val PLAYER_CHANNEL_ID = "player_channel"
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val recordingChannel = NotificationChannel(
                RECORDING_CHANNEL_ID,
                "Recording Service",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows recording status"
            }

            val playerChannel = NotificationChannel(
                PLAYER_CHANNEL_ID,
                "Audio Player",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Controls audio playback"
            }

            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager.createNotificationChannel(recordingChannel)
            notificationManager.createNotificationChannel(playerChannel)
        }
    }
}
