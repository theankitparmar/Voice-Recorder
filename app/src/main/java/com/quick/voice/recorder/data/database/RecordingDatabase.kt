// data/database/RecordingDatabase.kt
package com.quick.voice.recorder.data.database

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import android.content.Context

@Database(
    entities = [Recording::class],
    version = 1,
    exportSchema = false
)
abstract class RecordingDatabase : RoomDatabase() {
    abstract fun recordingDao(): RecordingDao
}
