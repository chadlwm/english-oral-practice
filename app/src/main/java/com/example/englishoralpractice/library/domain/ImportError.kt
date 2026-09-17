package com.example.englishoralpractice.library.domain

sealed class ImportError(val messageResId: Int) {
    
    data object UnsupportedFormat : ImportError(
        com.example.englishoralpractice.R.string.import_error_unsupported_format
    )
    
    data object NoVideoTrack : ImportError(
        com.example.englishoralpractice.R.string.import_error_no_video_track
    )
    
    data object CorruptFile : ImportError(
        com.example.englishoralpractice.R.string.import_error_corrupt
    )
    
    data object PermissionDenied : ImportError(
        com.example.englishoralpractice.R.string.import_error_permission
    )
    
    data object Unknown : ImportError(
        com.example.englishoralpractice.R.string.import_error_unknown
    )
}

sealed class ImportResult {
    data class Success(val videoId: Long) : ImportResult()
    data class Error(val error: ImportError) : ImportResult()
}
