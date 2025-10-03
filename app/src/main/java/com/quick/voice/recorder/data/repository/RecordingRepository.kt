package com.quick.voice.recorder.data.repository

import androidx.lifecycle.LiveData
import com.quick.voice.recorder.data.database.Recording
import com.quick.voice.recorder.data.database.RecordingDao

class RecordingRepository(private val recordingDao: RecordingDao) {

    // Use LiveData for Room database operations
    fun getAllRecordingsLiveData(): LiveData<List<Recording>> {
        return recordingDao.getAllRecordings()
    }

    suspend fun getRecordingById(id: Long): Recording? {
        return recordingDao.getRecordingById(id)
    }

    suspend fun insertRecording(recording: Recording) {
        recordingDao.insertRecording(recording)
    }

    suspend fun updateRecording(recording: Recording) {
        recordingDao.updateRecording(recording)
    }

    suspend fun deleteRecording(recording: Recording) {
        recordingDao.deleteRecording(recording)
    }

    suspend fun deleteAllRecordings() {
        recordingDao.deleteAllRecordings()
    }

    suspend fun searchRecordings(query: String): List<Recording> {
        return recordingDao.searchRecordings("%$query%")
    }
}