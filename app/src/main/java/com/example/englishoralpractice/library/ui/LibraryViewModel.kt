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
import com.example.englishoralpractice.subtitle.SubtitleManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class LibraryUiState(
    val isLoading: Boolean = false,
    val videos: List<VideoItem> = emptyList()
)

data class VideoItem(
    val id: Long,
    val title: String,
    val durationMs: Long,
    val uri: String,
    val subtitleUri: String? = null,
    val hasSubtitle: Boolean = false,
    val isPlayable: Boolean = true
)

sealed class LibraryEvent {
    data class VideoImportError(val error: ImportError) : LibraryEvent()
    data class ImportSuccess(val videoId: Long) : LibraryEvent()
    data class ReauthorizeSuccess(val videoId: Long) : LibraryEvent()
    data class NavigateToPlayer(val videoId: Long) : LibraryEvent()
    data class SubtitleImportSuccess(val videoId: Long) : LibraryEvent()
    data class SubtitleImportError(val message: String) : LibraryEvent()
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
            videoDao.getAllVideos().collect { videos ->
                val videoItems = withContext(Dispatchers.IO) {
                    videos.map { it.toVideoItem() }
                }
                _uiState.value = LibraryUiState(
                    isLoading = false,
                    videos = videoItems
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
                    _events.emit(LibraryEvent.VideoImportError(result.error))
                }
            }
            
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
    
    fun reauthorizeVideo(videoId: Long, newUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            when (val result = videoImporter.reauthorizeVideo(videoId, newUri)) {
                is ImportResult.Success -> {
                    _events.emit(LibraryEvent.ReauthorizeSuccess(result.videoId))
                }
                is ImportResult.Error -> {
                    _events.emit(LibraryEvent.VideoImportError(result.error))
                }
            }
            
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
    
    fun importSubtitle(videoId: Long, subtitleUri: Uri) {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            withContext(Dispatchers.IO) {
                val context = getApplication<EnglishOralPracticeApp>()
                
                if (!SubtitleManager.isAllowedSubtitleFormat(context, subtitleUri)) {
                    _events.emit(LibraryEvent.SubtitleImportError(
                        "Unsupported subtitle format. Only .srt files are supported."
                    ))
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@withContext
                }
                
                val video = videoDao.getVideoById(videoId)
                if (video == null) {
                    _events.emit(LibraryEvent.SubtitleImportError("Video not found"))
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@withContext
                }
                
                video.subtitleUri?.let { oldUri ->
                    SubtitleManager.releasePersistablePermission(context, Uri.parse(oldUri))
                }
                
                if (!SubtitleManager.takePersistablePermission(context, subtitleUri)) {
                    _events.emit(LibraryEvent.SubtitleImportError("Permission denied"))
                    _uiState.value = _uiState.value.copy(isLoading = false)
                    return@withContext
                }
                
                videoDao.updateSubtitleUri(videoId, subtitleUri.toString())
                _events.emit(LibraryEvent.SubtitleImportSuccess(videoId))
            }
            
            _uiState.value = _uiState.value.copy(isLoading = false)
        }
    }
    
    fun removeSubtitle(videoId: Long) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val context = getApplication<EnglishOralPracticeApp>()
                val video = videoDao.getVideoById(videoId)
                
                video?.subtitleUri?.let { subtitleUri ->
                    SubtitleManager.releasePersistablePermission(context, Uri.parse(subtitleUri))
                }
                
                videoDao.updateSubtitleUri(videoId, null)
            }
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
            withContext(Dispatchers.IO) {
                val video = videoDao.getVideoById(videoId)
                if (video != null) {
                    videoImporter.releasePersistablePermission(Uri.parse(video.uri))
                    video.subtitleUri?.let { subtitleUri ->
                        val context = getApplication<EnglishOralPracticeApp>()
                        SubtitleManager.releasePersistablePermission(context, Uri.parse(subtitleUri))
                    }
                    videoDao.deleteVideoById(videoId)
                }
            }
        }
    }
    
    private fun VideoEntity.toVideoItem(): VideoItem {
        val isPlayable = checkUriAccessible(uri)
        return VideoItem(
            id = id,
            title = title,
            durationMs = durationMs,
            uri = uri,
            subtitleUri = subtitleUri,
            hasSubtitle = subtitleUri != null,
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
