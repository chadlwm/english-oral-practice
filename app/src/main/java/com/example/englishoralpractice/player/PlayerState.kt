package com.example.englishoralpractice.player

enum class LoopMode {
    OFF,
    ALL,
    AB
}

data class ABLoopPoints(
    val startMs: Long,
    val endMs: Long
) {
    init {
        require(startMs < endMs) { "A-B loop: start must be less than end" }
    }
    
    fun contains(positionMs: Long): Boolean = positionMs in startMs..endMs
    
    fun clamp(positionMs: Long): Long = positionMs.coerceIn(startMs, endMs)
}

sealed class ABLoopState {
    data object Inactive : ABLoopState()
    data class SettingA(val pendingA: Long) : ABLoopState()
    data class Active(val points: ABLoopPoints) : ABLoopState()
}

sealed class ABLoopResult {
    data object Success : ABLoopResult()
    data class InvalidRange(val message: String) : ABLoopResult()
}

class PlayerStateManager(
    private val seekStepMs: Long = DEFAULT_SEEK_STEP_MS
) {
    companion object {
        const val DEFAULT_SEEK_STEP_MS = 5000L
    }
    
    private var _loopMode: LoopMode = LoopMode.OFF
    val loopMode: LoopMode get() = _loopMode
    
    private var _abLoopState: ABLoopState = ABLoopState.Inactive
    val abLoopState: ABLoopState get() = _abLoopState
    
    private var _duration: Long = 0L
    val duration: Long get() = _duration
    
    fun setDuration(durationMs: Long) {
        _duration = durationMs
    }
    
    fun cycleLoopMode(): LoopMode {
        _loopMode = when (_loopMode) {
            LoopMode.OFF -> LoopMode.ALL
            LoopMode.ALL -> LoopMode.AB
            LoopMode.AB -> {
                clearABLoop()
                LoopMode.OFF
            }
        }
        return _loopMode
    }
    
    fun setLoopMode(mode: LoopMode) {
        if (mode != LoopMode.AB && _loopMode == LoopMode.AB) {
            clearABLoop()
        }
        _loopMode = mode
    }
    
    fun setPointA(positionMs: Long): ABLoopResult {
        if (_loopMode != LoopMode.AB) {
            return ABLoopResult.InvalidRange("Not in A-B loop mode")
        }
        _abLoopState = ABLoopState.SettingA(positionMs.coerceIn(0, _duration))
        return ABLoopResult.Success
    }
    
    fun setPointB(positionMs: Long): ABLoopResult {
        val state = _abLoopState
        if (state !is ABLoopState.SettingA) {
            return ABLoopResult.InvalidRange("Set point A first")
        }
        
        val pointA = state.pendingA
        val clampedB = positionMs.coerceAtMost(_duration).coerceAtLeast(0)
        
        if (pointA >= clampedB) {
            _abLoopState = ABLoopState.Inactive
            return ABLoopResult.InvalidRange("Point A must be before point B")
        }
        
        _abLoopState = ABLoopState.Active(ABLoopPoints(pointA, clampedB))
        return ABLoopResult.Success
    }
    
    fun clearABLoop() {
        _abLoopState = ABLoopState.Inactive
    }
    
    fun calculateSeekPosition(currentPositionMs: Long, forward: Boolean): Long {
        val delta = if (forward) seekStepMs else -seekStepMs
        val targetPosition = currentPositionMs + delta
        
        return when (val state = _abLoopState) {
            is ABLoopState.Active -> {
                state.points.clamp(targetPosition)
            }
            else -> {
                targetPosition.coerceIn(0, _duration)
            }
        }
    }
    
    fun shouldLoopToStart(currentPositionMs: Long): Boolean {
        return when (_loopMode) {
            LoopMode.OFF -> false
            LoopMode.ALL -> currentPositionMs >= _duration - 100
            LoopMode.AB -> {
                val state = _abLoopState
                if (state is ABLoopState.Active) {
                    currentPositionMs >= state.points.endMs - 100
                } else {
                    false
                }
            }
        }
    }
    
    fun getLoopStartPosition(): Long {
        return when (val state = _abLoopState) {
            is ABLoopState.Active -> state.points.startMs
            else -> 0L
        }
    }
    
    fun isPositionInABLoop(positionMs: Long): Boolean {
        val state = _abLoopState
        return if (state is ABLoopState.Active) {
            state.points.contains(positionMs)
        } else {
            true
        }
    }
    
    fun clampPositionToABLoop(positionMs: Long): Long {
        val state = _abLoopState
        return if (state is ABLoopState.Active) {
            state.points.clamp(positionMs)
        } else {
            positionMs.coerceIn(0, _duration)
        }
    }
}
