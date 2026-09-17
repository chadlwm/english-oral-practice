package com.example.englishoralpractice.player

import android.app.Application
import android.net.Uri
import androidx.annotation.OptIn
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import com.example.englishoralpractice.EnglishOralPracticeApp
import com.example.englishoralpractice.library.data.VideoEntity
import com.example.englishoralpractice.subtitle.SubtitleCue
import com.example.englishoralpractice.subtitle.SubtitleLoadResult
import com.example.englishoralpractice.subtitle.SubtitleManager
import com.example.englishoralpractice.subtitle.SubtitleSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class PlayerUiState(
    val isLoading: Boolean = true,
    val video: VideoEntity? = null,
    val error: String? = null,
    val isPlaying: Boolean = false,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val loopMode: LoopMode = LoopMode.OFF,
    val abLoopState: ABLoopState = ABLoopState.Inactive,
    val subtitleEnabled: Boolean = true,
    val currentSubtitleText: String? = null,
    val subtitleSource: SubtitleSource = SubtitleSource.None,
    val seekStepMs: Long = PlayerStateManager.DEFAULT_SEEK_STEP_MS
)

sealed class PlayerEvent {
    data class ShowMessage(val message: String) : PlayerEvent()
    data class SubtitleError(val error: String, val usingEmbedded: Boolean) : PlayerEvent()
}

class PlayerViewModel(
    application: Application,
    savedStateHandle: SavedStateHandle
) : AndroidViewModel(application) {
    
    private val videoId: Long = savedStateHandle.get<Long>("videoId") ?: -1L
    
    private val app = application as EnglishOralPracticeApp
    private val videoDao = app.database.videoDao()
    
    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()
    
    private val _events = MutableSharedFlow<PlayerEvent>()
    val events: SharedFlow<PlayerEvent> = _events.asSharedFlow()
    
    private var _exoPlayer: ExoPlayer? = null
    val exoPlayer: ExoPlayer?
        get() = _exoPlayer
    
    private val stateManager = PlayerStateManager()
    
    private var externalSubtitleCues: List<SubtitleCue> = emptyList()
    private var positionUpdateJob: Job? = null
    
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
            loadSubtitles(video)
        }
    }
    
    @OptIn(UnstableApi::class)
    private fun initializePlayer(video: VideoEntity) {
        if (_exoPlayer != null) return
        
        val context = getApplication<Application>()
        _exoPlayer = ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(Uri.parse(video.uri))
            setMediaItem(mediaItem)
            repeatMode = Player.REPEAT_MODE_OFF
            prepare()
            
            addListener(object : Player.Listener {
                override fun onIsPlayingChanged(isPlaying: Boolean) {
                    _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                    if (isPlaying) {
                        startPositionUpdates()
                    } else {
                        stopPositionUpdates()
                    }
                }
                
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        val durationMs = duration.takeIf { it != C.TIME_UNSET } ?: 0L
                        stateManager.setDuration(durationMs)
                        _uiState.value = _uiState.value.copy(duration = durationMs)
                    }
                }
                
                override fun onPositionDiscontinuity(
                    oldPosition: Player.PositionInfo,
                    newPosition: Player.PositionInfo,
                    reason: Int
                ) {
                    handlePositionChange(newPosition.positionMs)
                }
            })
        }
    }
    
    private fun loadSubtitles(video: VideoEntity) {
        val context = getApplication<Application>()
        val videoUri = Uri.parse(video.uri)
        val subtitleUri = video.subtitleUri?.let { Uri.parse(it) }
        
        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                SubtitleManager.loadSubtitles(context, videoUri, subtitleUri)
            }
            
            when (result) {
                is SubtitleLoadResult.Success -> {
                    handleSubtitleSource(result.source)
                }
                is SubtitleLoadResult.FallbackToEmbedded -> {
                    handleSubtitleSource(result.source)
                    _events.emit(PlayerEvent.SubtitleError(result.externalError, true))
                }
                is SubtitleLoadResult.NoSubtitles -> {
                    _uiState.value = _uiState.value.copy(
                        subtitleSource = SubtitleSource.None,
                        subtitleEnabled = false
                    )
                    result.error?.let {
                        _events.emit(PlayerEvent.SubtitleError(it, false))
                    }
                }
            }
        }
    }
    
    @OptIn(UnstableApi::class)
    private fun handleSubtitleSource(source: SubtitleSource) {
        when (source) {
            is SubtitleSource.External -> {
                externalSubtitleCues = source.cues
                _exoPlayer?.let { player ->
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
                _uiState.value = _uiState.value.copy(subtitleSource = source)
            }
            is SubtitleSource.Embedded -> {
                _exoPlayer?.let { player ->
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                        .build()
                }
                _uiState.value = _uiState.value.copy(subtitleSource = source)
            }
            is SubtitleSource.None -> {
                _exoPlayer?.let { player ->
                    player.trackSelectionParameters = player.trackSelectionParameters
                        .buildUpon()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                        .build()
                }
                _uiState.value = _uiState.value.copy(
                    subtitleSource = source,
                    subtitleEnabled = false
                )
            }
        }
    }
    
    private fun startPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = viewModelScope.launch {
            while (true) {
                val position = _exoPlayer?.currentPosition ?: 0L
                handlePositionChange(position)
                delay(100)
            }
        }
    }
    
    private fun stopPositionUpdates() {
        positionUpdateJob?.cancel()
        positionUpdateJob = null
    }
    
    private fun handlePositionChange(positionMs: Long) {
        _uiState.value = _uiState.value.copy(currentPosition = positionMs)
        
        updateCurrentSubtitle(positionMs)
        
        if (stateManager.shouldLoopToStart(positionMs)) {
            val startPosition = stateManager.getLoopStartPosition()
            _exoPlayer?.seekTo(startPosition)
        }
    }
    
    private fun updateCurrentSubtitle(positionMs: Long) {
        if (!_uiState.value.subtitleEnabled) {
            _uiState.value = _uiState.value.copy(currentSubtitleText = null)
            return
        }
        
        val source = _uiState.value.subtitleSource
        if (source is SubtitleSource.External) {
            val cue = SubtitleManager.getCurrentCue(externalSubtitleCues, positionMs)
            _uiState.value = _uiState.value.copy(currentSubtitleText = cue?.text)
        }
    }
    
    fun play() {
        _exoPlayer?.play()
    }
    
    fun pause() {
        _exoPlayer?.pause()
    }
    
    fun togglePlayPause() {
        val player = _exoPlayer ?: return
        if (player.isPlaying) {
            player.pause()
        } else {
            player.play()
        }
    }
    
    fun seekTo(positionMs: Long) {
        val clampedPosition = stateManager.clampPositionToABLoop(positionMs)
        _exoPlayer?.seekTo(clampedPosition)
    }
    
    fun seekForward() {
        val currentPosition = _exoPlayer?.currentPosition ?: 0L
        val newPosition = stateManager.calculateSeekPosition(currentPosition, forward = true)
        _exoPlayer?.seekTo(newPosition)
    }
    
    fun seekBackward() {
        val currentPosition = _exoPlayer?.currentPosition ?: 0L
        val newPosition = stateManager.calculateSeekPosition(currentPosition, forward = false)
        _exoPlayer?.seekTo(newPosition)
    }
    
    fun cycleLoopMode() {
        val newMode = stateManager.cycleLoopMode()
        _uiState.value = _uiState.value.copy(
            loopMode = newMode,
            abLoopState = stateManager.abLoopState
        )
        
        _exoPlayer?.repeatMode = when (newMode) {
            LoopMode.OFF -> Player.REPEAT_MODE_OFF
            LoopMode.ALL -> Player.REPEAT_MODE_ONE
            LoopMode.AB -> Player.REPEAT_MODE_OFF
        }
    }
    
    fun setPointA() {
        val currentPosition = _exoPlayer?.currentPosition ?: 0L
        val result = stateManager.setPointA(currentPosition)
        
        when (result) {
            is ABLoopResult.Success -> {
                _uiState.value = _uiState.value.copy(abLoopState = stateManager.abLoopState)
            }
            is ABLoopResult.InvalidRange -> {
                viewModelScope.launch {
                    _events.emit(PlayerEvent.ShowMessage(result.message))
                }
            }
        }
    }
    
    fun setPointB() {
        val currentPosition = _exoPlayer?.currentPosition ?: 0L
        val result = stateManager.setPointB(currentPosition)
        
        when (result) {
            is ABLoopResult.Success -> {
                _uiState.value = _uiState.value.copy(abLoopState = stateManager.abLoopState)
            }
            is ABLoopResult.InvalidRange -> {
                _uiState.value = _uiState.value.copy(abLoopState = stateManager.abLoopState)
                viewModelScope.launch {
                    _events.emit(PlayerEvent.ShowMessage(result.message))
                }
            }
        }
    }
    
    fun clearABLoop() {
        stateManager.clearABLoop()
        _uiState.value = _uiState.value.copy(abLoopState = stateManager.abLoopState)
    }
    
    fun toggleSubtitles() {
        val currentSource = _uiState.value.subtitleSource
        if (currentSource is SubtitleSource.None) {
            viewModelScope.launch {
                _events.emit(PlayerEvent.ShowMessage("No subtitles available"))
            }
            return
        }
        
        val newEnabled = !_uiState.value.subtitleEnabled
        _uiState.value = _uiState.value.copy(subtitleEnabled = newEnabled)
        
        if (currentSource is SubtitleSource.Embedded) {
            toggleEmbeddedSubtitles(newEnabled)
        }
    }
    
    @OptIn(UnstableApi::class)
    private fun toggleEmbeddedSubtitles(enabled: Boolean) {
        _exoPlayer?.let { player ->
            player.trackSelectionParameters = player.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, !enabled)
                .build()
        }
    }
    
    override fun onCleared() {
        super.onCleared()
        stopPositionUpdates()
        releasePlayer()
    }
    
    private fun releasePlayer() {
        _exoPlayer?.release()
        _exoPlayer = null
    }
}
