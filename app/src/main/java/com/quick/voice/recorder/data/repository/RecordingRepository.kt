// data/repository/RecordingRepository.kt
package com.quick.voice.recorder.data.repository

import androidx.lifecycle.LiveData
import com.quick.voice.recorder.data.database.Recording
import com.quick.voice.recorder.data.database.RecordingDao

class RecordingRepository(private val recordingDao: RecordingDao) {
    
    fun getAllRecordings(): LiveData<List<Recording>> = recordingDao.getAllRecordings()
    
    suspend fun getRecordingById(id: Long): Recording? = recordingDao.getRecordingById(id)
    
    suspend fun insertRecording(recording: Recording): Long = recordingDao.insertRecording(recording)
    
    suspend fun deleteRecording(recording: Recording) = recordingDao.deleteRecording(recording)
    
    suspend fun deleteRecordingById(id: Long) = recordingDao.deleteRecordingById(id)
}
