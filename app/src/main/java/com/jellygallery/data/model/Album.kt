package com.jellygallery.data.model

import android.net.Uri

data class Album(
    val id: String,
    val name: String,
    val relativePath: String,
    val count: Int,
    val thumbnailUri: Uri?
)
