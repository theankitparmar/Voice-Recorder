package com.quick.voice.recorder.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.quick.voice.recorder.data.database.Recording
import com.quick.voice.recorder.data.repository.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File

class RecordingsViewModel(
    private val repository: RecordingRepository
) : ViewModel() {

    private val _recordings = MutableStateFlow<List<Recording>>(emptyList())
    val recordings: StateFlow<List<Recording>> = _recordings.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        loadRecordings()
    }

    fun loadRecordings() {
        _isLoading.value = true
        viewModelScope.launch {
            try {
                // Use LiveData instead of Flow
                repository.getAllRecordingsLiveData().observeForever { recordingsList ->
                    _recordings.value = recordingsList ?: emptyList()
                    _errorMessage.value = null
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to load recordings: ${e.message}"
                Log.e("RecordingsViewModel", "loadRecordings error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun refreshRecordings() {
        loadRecordings()
    }

    fun deleteRecording(recording: Recording) {
        viewModelScope.launch {
            try {
                // Delete the physical file first
                val file = File(recording.filePath)
                if (file.exists()) {
                    file.delete()
                }

                // Then delete from database
                repository.deleteRecording(recording)

                // Reload the list
                loadRecordings()
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete recording: ${e.message}"
                Log.e("RecordingsViewModel", "deleteRecording error: ${e.message}", e)
            }
        }
    }

    fun deleteAllRecordings() {
        viewModelScope.launch {
            try {
                _isLoading.value = true

                // Delete all physical files first
                _recordings.value.forEach { recording ->
                    val file = File(recording.filePath)
                    if (file.exists()) {
                        file.delete()
                    }
                }

                // Then delete all from database
                repository.deleteAllRecordings()

                // Clear the list
                _recordings.value = emptyList()
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Failed to delete all recordings: ${e.message}"
                Log.e("RecordingsViewModel", "deleteAllRecordings error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun renameRecording(recording: Recording, newName: String) {
        viewModelScope.launch {
            try {
                val originalFile = File(recording.filePath)
                if (!originalFile.exists()) {
                    _errorMessage.value = "Original file not found"
                    return@launch
                }

                // Ensure the new name has proper extension
                val fileNameWithExtension = if (newName.contains(".")) {
                    newName
                } else {
                    "$newName.${getFileExtension(recording.fileName)}"
                }

                val newFile = File(originalFile.parent, fileNameWithExtension)

                // Check if new file already exists
                if (newFile.exists()) {
                    _errorMessage.value = "A file with this name already exists"
                    return@launch
                }

                // Rename the physical file
                if (originalFile.renameTo(newFile)) {
                    // Update the database
                    val updatedRecording = recording.copy(
                        fileName = fileNameWithExtension,
                        filePath = newFile.absolutePath
                    )
                    repository.updateRecording(updatedRecording)

                    // Reload recordings
                    loadRecordings()
                    _errorMessage.value = null
                } else {
                    _errorMessage.value = "Failed to rename file"
                }
            } catch (e: Exception) {
                _errorMessage.value = "Failed to rename recording: ${e.message}"
                Log.e("RecordingsViewModel", "renameRecording error: ${e.message}", e)
            }
        }
    }

    fun searchRecordings(query: String) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                if (query.isBlank()) {
                    // If query is empty, reload all recordings
                    loadRecordings()
                } else {
                    // For search, we'll filter the current list (you can implement proper search in repository)
                    val currentList = _recordings.value
                    val filteredList = currentList.filter { recording ->
                        recording.fileName.contains(query, ignoreCase = true) ||
                                recording.filePath.contains(query, ignoreCase = true)
                    }
                    _recordings.value = filteredList
                }
                _errorMessage.value = null
            } catch (e: Exception) {
                _errorMessage.value = "Search failed: ${e.message}"
                Log.e("RecordingsViewModel", "searchRecordings error: ${e.message}", e)
            } finally {
                _isLoading.value = false
            }
        }
    }

    private fun getFileExtension(fileName: String): String {
        return fileName.substringAfterLast('.', "m4a") // default to m4a if no extension
    }

    fun clearErrorMessage() {
        _errorMessage.value = null
    }
}

class RecordingsViewModelFactory(
    private val repository: RecordingRepository
) : androidx.lifecycle.ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(RecordingsViewModel::class.java)) {
            return RecordingsViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}