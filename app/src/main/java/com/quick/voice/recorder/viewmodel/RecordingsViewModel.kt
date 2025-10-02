// viewmodel/RecordingsViewModel.kt
package com.quick.voice.recorder.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.quick.voice.recorder.data.repository.RecordingRepository

class RecordingsViewModel(private val repository: RecordingRepository) : ViewModel() {
    
    val recordings = repository.getAllRecordings()
}

class RecordingsViewModelFactory(
    private val repository: RecordingRepository
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecordingsViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return RecordingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
