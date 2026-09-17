package com.example.englishoralpractice.player

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.englishoralpractice.EnglishOralPracticeApp
import com.example.englishoralpractice.library.data.VideoEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class PlayerUiState(
    val isLoading: Boolean = true,
    val video: VideoEntity? = null,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L
)

class PlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    
    private val videoId: Long = savedStateHandle.get<Long>("videoId") ?: -1L
    
    private val app = application as EnglishOralPracticeApp
    private val videoDao = app.database.videoDao()
    
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer?
        get() = _exoPlayer
    
    init {
        loadVideo()
    }
    
    private fun loadVideo() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            
            val video = videoDao.getVideoById(videoId)
            if (video == null) {
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    error = "Video not found"
                )
                return@launch
            }
            
            _uiState.value = _uiState.value.copy(
                isLoading = false,
                video = video
            )
            
            initializePlayer(video)
        }
    }
    
    @OptIn(UnstableApi::class)
    private fun initializePlayer(video: VideoEntity) {
        if (_exoPlayer != null) return
        
        val context = getApplication<Application>()
        _exoPlayer = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(video.uri))
            setMediaItem(mediaItem)
            prepare()
            
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                }
            })
        }
    }
    
    fun play() {
        _exoPlayer?.play()
    }
    
    fun pause() {
        _exoPlayer?.pause()
    }
    
    fun seekTo(positionMs: Long) {
        _exoPlayer?.seekTo(positionMs)
    }
    
    override fun onCleared() {
        super.onCleared()
        releasePlayer()
    }
    
    private fun releasePlayer() {
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
