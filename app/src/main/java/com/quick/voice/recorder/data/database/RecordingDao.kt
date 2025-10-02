// data/database/RecordingDao.kt
package com.quick.voice.recorder.data.database

import androidx.lifecycle.LiveData
import androidx.room.*

@Dao
interface RecordingDao {
    
    @Query("SELECT * FROM recordings ORDER BY createdDate DESC")
    fun getAllRecordings(): LiveData<List<Recording>>
    
    @Query("SELECT * FROM recordings WHERE id = :id")
    suspend fun getRecordingById(id: Long): Recording?
    
    @Insert
    suspend fun insertRecording(recording: Recording): Long
    
    @Delete
    suspend fun deleteRecording(recording: Recording)
    
    @Query("DELETE FROM recordings WHERE id = :id")
    suspend fun deleteRecordingById(id: Long)
}
