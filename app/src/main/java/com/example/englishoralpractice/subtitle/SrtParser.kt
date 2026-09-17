package com.example.englishoralpractice.subtitle

import android.content.Context
import android.net.Uri

/**
 * SRT Subtitle Parser - Stub for P1
 * 
 * Planned functionality:
 * - Parse .srt files only
 * - Parse fail → show message + fall back to embedded subtitles
 * - No embedded subtitles → disable track without crash
 * - External SRT preferred over embedded
 */
data class SubtitleCue(
    val index: Int,
    val startTimeMs: Long,
    val endTimeMs: Long,
    val text: String
)

sealed class SrtParseResult {
    data class Success(val cues: List<SubtitleCue>) : SrtParseResult()
    data class Error(val message: String) : SrtParseResult()
}

object SrtParser {
    
    /**
     * Parse an SRT file from the given URI.
     * 
     * Stub implementation - returns empty result for P1.
     */
    fun parse(context: Context, uri: Uri): SrtParseResult {
        // TODO: Implement SRT parsing in P1
        // 1. Read file content from URI
        // 2. Parse SRT format:
        //    - Index number
        //    - Timestamp: HH:MM:SS,mmm --> HH:MM:SS,mmm
        //    - Text (can be multiline)
        //    - Empty line separator
        // 3. Return list of SubtitleCue or Error
        
        return SrtParseResult.Error("SRT parsing not yet implemented (P1 stub)")
    }
    
    /**
     * Convert timestamp string to milliseconds.
     * Format: HH:MM:SS,mmm
     */
    fun parseTimestamp(timestamp: String): Long? {
        // TODO: Implement in P1
        // Example: "00:01:23,456" -> 83456
        return null
    }
}

/**
 * Subtitle track manager - handles subtitle source selection.
 * 
 * Priority:
 * 1. External SRT file (if available and valid)
 * 2. Embedded subtitle track (if available)
 * 3. None (disable subtitles gracefully)
 */
object SubtitleManager {
    
    /**
     * Get the best available subtitle source for a video.
     * 
     * Stub implementation for P1.
     */
    fun getSubtitleSource(
        context: Context,
        videoUri: Uri,
        externalSrtUri: Uri?
    ): SubtitleSource {
        // TODO: Implement in P1
        return SubtitleSource.None
    }
}

sealed class SubtitleSource {
    data class External(val uri: Uri, val cues: List<SubtitleCue>) : SubtitleSource()
    data class Embedded(val trackIndex: Int) : SubtitleSource()
    data object None : SubtitleSource()
}
