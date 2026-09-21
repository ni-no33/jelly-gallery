package com.jellygallery.data.model

import android.net.Uri

data class MediaItem(
    val id: Long,
    val uri: Uri,
    val path: String,
    val displayName: String,
    val mimeType: String,
    val isVideo: Boolean,
    val durationMs: Long = 0L,
    val dateAdded: Long,
    val dateTaken: Long,
    val size: Long,
    val width: Int,
    val height: Int,
    val albumName: String,
    val isFavorite: Boolean = false
) {
    val formattedDuration: String
        get() {
            if (!isVideo || durationMs <= 0) return ""
            val totalSeconds = durationMs / 1000
            val minutes = totalSeconds / 60
            val seconds = totalSeconds % 60
            return "%d:%02d".format(minutes, seconds)
        }
}
