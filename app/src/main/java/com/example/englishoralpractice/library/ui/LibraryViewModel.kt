package com.example.englishoralpractice.library.ui

import android.app.Application
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.englishoralpractice.EnglishOralPracticeApp
import com.example.englishoralpractice.library.VideoImporter
import com.example.englishoralpractice.library.data.VideoEntity
import com.example.englishoralpractice.library.domain.ImportError
import com.example.englishoralpractice.library.domain.ImportResult
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class LibraryUiState(
    val isLoading: Boolean = false,
    val videos: List<VideoItem> = emptyList()
)

data class VideoItem(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val uri: String,
    val isPlayable: Boolean = true
)

sealed class LibraryEvent {
    data class ImportError(val error: com.example.englishoralpractice.library.domain.ImportError) : LibraryEvent()
    data class ImportSuccess(val videoId: Long) : LibraryEvent()
    data class NavigateToPlayer(val videoId: Long) : LibraryEvent()
}

class LibraryViewModel(application: Application) : AndroidViewModel(application) {
    
    private val app = application as EnglishOralPracticeApp
    private val videoDao = app.database.videoDao()
    private val videoImporter = VideoImporter(application, videoDao)
    
    private val _uiState = MutableStateFlow(LibraryUiState(isLoading = true))
    val uiState: StateFlow<LibraryUiState> = _uiState
    
    private val _events = MutableSharedFlow<LibraryEvent>()
    val events: SharedFlow<LibraryEvent> = _events.asSharedFlow()
    
    init {
        viewModelScope.launch {
            videoDao.getAllVideos()
                .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
                .collect { videos ->
                    _uiState.value = LibraryUiState(
                        isLoading = false,
                        videos = videos.map { it.toVideoItem() }
                    )
                }
        }
    }
    
    fun importVideo(uri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            when (val result = videoImporter.importVideo(uri)) {
                is ImportResult.Success -> {
                    _events.emit(LibraryEvent.ImportSuccess(result.videoId))
                }
                is ImportResult.Error -> {
                    _events.emit(LibraryEvent.ImportError(result.error))
                }
            }
            
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
    
    fun onVideoClick(videoId: Long) {
        viewModelScope.launch {
            val video = _uiState.value.videos.find { it.id == videoId }
            if (video?.isPlayable == true) {
                _events.emit(LibraryEvent.NavigateToPlayer(videoId))
            }
        }
    }
    
    fun deleteVideo(videoId: Long) {
        viewModelScope.launch {
            videoDao.deleteVideoById(videoId)
        }
    }
    
    private fun VideoEntity.toVideoItem(): VideoItem {
        val isPlayable = checkUriAccessible(uri)
        return VideoItem(
            id = id,
            title = title,
            durationMs = durationMs,
            uri = uri,
            isPlayable = isPlayable
        )
    }
    
    private fun checkUriAccessible(uriString: String): Boolean {
        return try {
            val uri = Uri.parse(uriString)
            val context = getApplication<EnglishOralPracticeApp>()
            context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false
        } catch (e: Exception) {
            false
        }
    }
}
