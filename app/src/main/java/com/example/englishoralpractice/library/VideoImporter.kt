package com.example.englishoralpractice.library

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.media.MediaExtractor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.provider.OpenableColumns
import android.webkit.MimeTypeMap
import com.example.englishoralpractice.library.data.VideoDao
import com.example.englishoralpractice.library.data.VideoEntity
import com.example.englishoralpractice.library.domain.ImportError
import com.example.englishoralpractice.library.domain.ImportResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class VideoImporter(
    private val context: Context,
    private val videoDao: VideoDao
) {
    companion object {
        private val ALLOWED_EXTENSIONS = setOf("mp4", "mkv", "webm")
        private val ALLOWED_MIME_TYPES = setOf(
            "video/mp4",
            "video/x-matroska",
            "video/webm"
        )
    }
    
    suspend fun importVideo(uri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            if (!isAllowedFormat(uri)) {
                return@withContext ImportResult.Error(ImportError.UnsupportedFormat)
            }
            
            val metadata = extractMetadata(uri)
                ?: return@withContext ImportResult.Error(ImportError.CorruptFile)
            
            if (!hasVideoTrack(uri)) {
                return@withContext ImportResult.Error(ImportError.NoVideoTrack)
            }
            
            if (!takePersistablePermission(uri)) {
                return@withContext ImportResult.Error(ImportError.PermissionDenied)
            }
            
            val existingVideo = videoDao.getVideoByUri(uri.toString())
            if (existingVideo != null) {
                return@withContext ImportResult.Success(existingVideo.id)
            }
            
            val video = VideoEntity(
                title = metadata.title,
                durationMs = metadata.durationMs,
                uri = uri.toString()
            )
            
            val videoId = videoDao.insertVideo(video)
            ImportResult.Success(videoId)
            
        } catch (e: SecurityException) {
            ImportResult.Error(ImportError.PermissionDenied)
        } catch (e: Exception) {
            ImportResult.Error(ImportError.Unknown)
        }
    }
    
    suspend fun reauthorizeVideo(videoId: Long, newUri: Uri): ImportResult = withContext(Dispatchers.IO) {
        try {
            val existingVideo = videoDao.getVideoById(videoId)
                ?: return@withContext ImportResult.Error(ImportError.Unknown)
            
            if (!isAllowedFormat(newUri)) {
                return@withContext ImportResult.Error(ImportError.UnsupportedFormat)
            }
            
            val metadata = extractMetadata(newUri)
                ?: return@withContext ImportResult.Error(ImportError.CorruptFile)
            
            if (!hasVideoTrack(newUri)) {
                return@withContext ImportResult.Error(ImportError.NoVideoTrack)
            }
            
            if (!takePersistablePermission(newUri)) {
                return@withContext ImportResult.Error(ImportError.PermissionDenied)
            }
            
            releasePersistablePermission(Uri.parse(existingVideo.uri))
            
            val updatedVideo = existingVideo.copy(
                uri = newUri.toString(),
                title = metadata.title,
                durationMs = metadata.durationMs
            )
            videoDao.updateVideo(updatedVideo)
            
            ImportResult.Success(videoId)
            
        } catch (e: SecurityException) {
            ImportResult.Error(ImportError.PermissionDenied)
        } catch (e: Exception) {
            ImportResult.Error(ImportError.Unknown)
        }
    }
    
    fun releasePersistablePermission(uri: Uri) {
        try {
            val releaseFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.releasePersistableUriPermission(uri, releaseFlags)
        } catch (e: SecurityException) {
            // Permission may already be released or was never held
        }
    }
    
    private fun takePersistablePermission(uri: Uri): Boolean {
        return try {
            val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION
            context.contentResolver.takePersistableUriPermission(uri, takeFlags)
            true
        } catch (e: SecurityException) {
            false
        }
    }
    
    private fun isAllowedFormat(uri: Uri): Boolean {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null && mimeType in ALLOWED_MIME_TYPES) {
            return true
        }
        
        val extension = getFileExtension(uri)?.lowercase()
        return extension in ALLOWED_EXTENSIONS
    }
    
    private fun getFileExtension(uri: Uri): String? {
        val mimeType = context.contentResolver.getType(uri)
        if (mimeType != null) {
            val ext = MimeTypeMap.getSingleton().getExtensionFromMimeType(mimeType)
            if (ext != null) return ext
        }
        
        val fileName = getFileName(uri)
        return fileName?.substringAfterLast('.', "")?.takeIf { it.isNotEmpty() }
    }
    
    fun getFileName(uri: Uri): String? {
        var result: String? = null
        if (uri.scheme == ContentResolver.SCHEME_CONTENT) {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (nameIndex >= 0) {
                        result = cursor.getString(nameIndex)
                    }
                }
            }
        }
        if (result == null) {
            result = uri.path?.substringAfterLast('/')
        }
        return result
    }
    
    private data class VideoMetadata(
        val title: String,
        val durationMs: Long
    )
    
    private fun extractMetadata(uri: Uri): VideoMetadata? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            
            val durationStr = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)
            val durationMs = durationStr?.toLongOrNull() ?: return null
            
            val title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() }
                ?: getFileName(uri)?.substringBeforeLast('.')
                ?: "Untitled"
            
            VideoMetadata(title, durationMs)
        } catch (e: Exception) {
            null
        } finally {
            try {
                retriever.release()
            } catch (e: Exception) {
                // Ignore release errors
            }
        }
    }
    
    private fun hasVideoTrack(uri: Uri): Boolean {
        val extractor = MediaExtractor()
        return try {
            context.contentResolver.openFileDescriptor(uri, "r")?.use { pfd ->
                extractor.setDataSource(pfd.fileDescriptor)
                for (i in 0 until extractor.trackCount) {
                    val format = extractor.getTrackFormat(i)
                    val mime = format.getString(android.media.MediaFormat.KEY_MIME)
                    if (mime?.startsWith("video/") == true) {
                        return true
                    }
                }
            }
            false
        } catch (e: Exception) {
            false
        } finally {
            extractor.release()
        }
    }
}
