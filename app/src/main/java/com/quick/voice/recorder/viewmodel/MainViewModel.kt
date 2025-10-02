// viewmodel/MainViewModel.kt
package com.quick.voice.recorder.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quick.voice.recorder.data.database.Recording
import com.quick.voice.recorder.data.repository.RecordingRepository
import kotlinx.coroutines.launch
import java.io.File

class MainViewModel(private val repository: RecordingRepository) : ViewModel() {
    
    fun saveRecording(filePath: String, duration: Long) {
        viewModelScope.launch {
            val file = File(filePath)
            val recording = Recording(
                fileName = file.nameWithoutExtension,
                filePath = filePath,
                duration = duration,
                createdDate = System.currentTimeMillis(),
                fileSize = file.length()
            )
            repository.insertRecording(recording)
        }
    }
}

class MainViewModelFactory(
    private val repository: RecordingRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(MainViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return MainViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
