package com.quick.voice.recorder.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.quick.voice.recorder.data.repository.RecordingRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class PlayerViewModel (
    private val repository: RecordingRepository
) : ViewModel() {
    
    private val _playbackState = MutableStateFlow<PlaybackState>(PlaybackState.Idle)
    val playbackState: StateFlow<PlaybackState> = _playbackState
    
    sealed class PlaybackState {
        object Idle : PlaybackState()
        object Loading : PlaybackState()
        data class Playing(val currentPosition: Int, val duration: Int) : PlaybackState()
        object Paused : PlaybackState()
        object Stopped : PlaybackState()
        data class Error(val message: String) : PlaybackState()
    }
    
    fun updatePlaybackState(state: PlaybackState) {
        viewModelScope.launch {
            _playbackState.value = state
        }
    }
}

class PlayerViewModelFactory(
    private val repository: RecordingRepository
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(PlayerViewModel::class.java)) {
            return PlayerViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}