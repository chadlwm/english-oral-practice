package com.example.englishoralpractice.subtitle

import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.webkit.MimeTypeMap

sealed class SubtitleSource {
    data class External(val uri: Uri, val cues: List<SubtitleCue>) : SubtitleSource()
    data class Embedded(val trackIndex: Int) : SubtitleSource()
    data object None : SubtitleSource()
}

sealed class SubtitleLoadResult {
    data class Success(val source: SubtitleSource) : SubtitleLoadResult()
    data class FallbackToEmbedded(
        val source: SubtitleSource.Embedded,
        val externalError: String
    ) : SubtitleLoadResult()
    data class NoSubtitles(val error: String?) : SubtitleLoadResult()
}

object SubtitleManager {
    
    private val ALLOWED_SUBTITLE_EXTENSIONS = setOf("srt")
    
    fun isAllowedSubtitleFormat(context: Context, uri: Uri): Boolean {
        val extension = getFileExtension(context, uri)?.lowercase()
        return extension in ALLOWED_SUBTITLE_EXTENSIONS
    }
    
    private fun getFileExtension(context: Context, uri: Uri): String? {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (ext != null) return ext
        }
        
        val fileName = uri.lastPathSegment
        return fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
    }
    
    fun takePersistablePermission(context: Context, uri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            true
        } catch (e: SecurityException) {
            false
        }
    }
    
    fun releasePersistablePermission(context: Context, uri: Uri) {
        try {
            val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, releaseFlags)
        } catch (e: SecurityException) {
            // Permission may already be released
        }
    }
    
    fun loadSubtitles(
        context: Context,
        videoUri: Uri,
        externalSrtUri: Uri?
    ): SubtitleLoadResult {
        if (externalSrtUri != null) {
            when (val parseResult = SrtParser.parse(context, externalSrtUri)) {
                is SrtParseResult.Success -> {
                    return SubtitleLoadResult.Success(
                        SubtitleSource.External(externalSrtUri, parseResult.cues)
                    )
                }
                is SrtParseResult.Error -> {
                    val embeddedTrack = findEmbeddedSubtitleTrack(context, videoUri)
                    return if (embeddedTrack != null) {
                        SubtitleLoadResult.FallbackToEmbedded(
                            SubtitleSource.Embedded(embeddedTrack),
                            parseResult.message
                        )
                    } else {
                        SubtitleLoadResult.NoSubtitles(parseResult.message)
                    }
                }
            }
        }
        
        val embeddedTrack = findEmbeddedSubtitleTrack(context, videoUri)
        return if (embeddedTrack != null) {
            SubtitleLoadResult.Success(SubtitleSource.Embedded(embeddedTrack))
        } else {
            SubtitleLoadResult.NoSubtitles(null)
        }
    }
    
    private fun findEmbeddedSubtitleTrack(context: Context, videoUri: Uri): Int? {
        val extractor = MediaExtractor()
        return try {
            context.contentResolver.openFileDescriptor(videoUri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(MediaFormat.KEY_MIME)
                    if (mime?.startsWith("text/") == true || 
                        mime?.startsWith("application/x-subrip") == true ||
                        mime == "application/ttml+xml") {
                        return i
                    }
                }
            }
            null
        } catch (e: Exception) {
            null
        } finally {
            extractor.release()
        }
    }
    
    fun getCurrentCue(cues: List<SubtitleCue>, positionMs: Long): SubtitleCue? {
        return cues.find { cue ->
            positionMs >= cue.startTimeMs && positionMs <= cue.endTimeMs
        }
    }
}
