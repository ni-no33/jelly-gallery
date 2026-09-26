package com.jellygallery.data.repository

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import com.jellygallery.data.model.Album
import com.jellygallery.data.model.AlbumSortOrder
import com.jellygallery.data.model.MediaItem
import com.jellygallery.data.model.MediaSortOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    fun hasManageStoragePermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true
        }
    }

    suspend fun getMediaList(
        albumPath: String? = null,
        onlyFavorites: Boolean = false,
        includeTrashed: Boolean = false,
        sortOrder: MediaSortOrder = MediaSortOrder.DATE_DESC
    ): List<MediaItem> = withContext(Dispatchers.IO) {
        val mediaList = mutableListOf<MediaItem>()

        val projection = arrayOf(
            MediaStore.MediaColumns._ID,
            MediaStore.MediaColumns.DATA,
            MediaStore.MediaColumns.DISPLAY_NAME,
            MediaStore.MediaColumns.MIME_TYPE,
            MediaStore.MediaColumns.DATE_ADDED,
            MediaStore.MediaColumns.DATE_TAKEN,
            MediaStore.MediaColumns.SIZE,
            MediaStore.MediaColumns.WIDTH,
            MediaStore.MediaColumns.HEIGHT,
            MediaStore.MediaColumns.BUCKET_DISPLAY_NAME,
            MediaStore.MediaColumns.RELATIVE_PATH,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.MediaColumns.IS_FAVORITE else MediaStore.MediaColumns._ID
        )

        val uri = MediaStore.Files.getContentUri("external")
        val selectionList = mutableListOf<String>()
        val selectionArgsList = mutableListOf<String>()

        selectionList.add("(${MediaStore.Files.FileColumns.MEDIA_TYPE} = ? OR ${MediaStore.Files.FileColumns.MEDIA_TYPE} = ?)")
        selectionArgsList.add(MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString())
        selectionArgsList.add(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString())

        if (onlyFavorites && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            selectionList.add("${MediaStore.MediaColumns.IS_FAVORITE} = 1")
        }

        if (!albumPath.isNullOrEmpty()) {
            selectionList.add("${MediaStore.MediaColumns.BUCKET_DISPLAY_NAME} = ?")
            selectionArgsList.add(albumPath)
        }

        val selection = selectionList.joinToString(" AND ")
        val selectionArgs = selectionArgsList.toTypedArray()

        val sortClause = when (sortOrder) {
            MediaSortOrder.DATE_DESC -> "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC"
            MediaSortOrder.DATE_ASC -> "${MediaStore.MediaColumns.DATE_TAKEN} ASC, ${MediaStore.MediaColumns.DATE_ADDED} ASC"
            MediaSortOrder.NAME_ASC -> "${MediaStore.MediaColumns.DISPLAY_NAME} ASC"
            MediaSortOrder.NAME_DESC -> "${MediaStore.MediaColumns.DISPLAY_NAME} DESC"
        }

        try {
            val queryArgs = Bundle().apply {
                putString(ContentResolver.QUERY_ARG_SQL_SELECTION, selection)
                putStringArray(ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS, selectionArgs)
                putString(ContentResolver.QUERY_ARG_SQL_SORT_ORDER, sortClause)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    putInt(
                        MediaStore.QUERY_ARG_MATCH_TRASHED,
                        if (includeTrashed) MediaStore.MATCH_ONLY else MediaStore.MATCH_EXCLUDE
                    )
                }
            }

            val cursor = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                contentResolver.query(uri, projection, queryArgs, null)
            } else {
                contentResolver.query(uri, projection, selection, selectionArgs, sortClause)
            }

            cursor?.use { c ->
                val idCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dataCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val nameCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateAddedCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dateTakenCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val sizeCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val widthCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val bucketCol = c.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val favCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    c.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                } else -1

                while (c.moveToNext()) {
                    val id = c.getLong(idCol)
                    val mime = c.getString(mimeCol) ?: ""
                    val isVideo = mime.startsWith("video/")
                    val contentUri = if (isVideo) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }

                    val isFavorite = if (favCol >= 0) c.getInt(favCol) == 1 else false
                    val albumName = c.getString(bucketCol) ?: "その他"

                    mediaList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            path = c.getString(dataCol) ?: "",
                            displayName = c.getString(nameCol) ?: "",
                            mimeType = mime,
                            isVideo = isVideo,
                            dateAdded = c.getLong(dateAddedCol),
                            dateTaken = c.getLong(dateTakenCol),
                            size = c.getLong(sizeCol),
                            width = c.getInt(widthCol),
                            height = c.getInt(heightCol),
                            albumName = albumName,
                            isFavorite = isFavorite
                        )
                    )
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        mediaList
    }

    suspend fun getAlbums(sortOrder: AlbumSortOrder = AlbumSortOrder.COUNT_DESC): List<Album> = withContext(Dispatchers.IO) {
        val albumMap = mutableMapOf<String, Pair<Int, Uri?>>()
        val allMedia = getMediaList()

        for (item in allMedia) {
            val name = item.albumName
            val current = albumMap[name]
            if (current == null) {
                albumMap[name] = Pair(1, item.uri)
            } else {
                albumMap[name] = Pair(current.first + 1, current.second)
            }
        }

        val list = albumMap.map { (name, info) ->
            Album(
                id = name,
                name = name,
                relativePath = "Pictures/$name/",
                count = info.first,
                thumbnailUri = info.second
            )
        }

        when (sortOrder) {
            AlbumSortOrder.COUNT_DESC -> list.sortedByDescending { it.count }
            AlbumSortOrder.NAME_ASC -> list.sortedBy { it.name.lowercase() }
        }
    }

    fun createTrashPendingIntent(uris: List<Uri>, trash: Boolean): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createTrashRequest(contentResolver, uris, trash)
        } else {
            null
        }
    }

    fun createDeletePendingIntent(uris: List<Uri>): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, uris)
        } else {
            null
        }
    }

    suspend fun directDeleteOrTrash(item: MediaItem): Boolean = withContext(Dispatchers.IO) {
        try {
            val file = File(item.path)
            if (file.exists()) {
                file.delete()
            }
            contentResolver.delete(item.uri, null, null) > 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun createFavoritePendingIntent(uris: List<Uri>, isFavorite: Boolean): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createFavoriteRequest(contentResolver, uris, isFavorite)
        } else {
            null
        }
    }

    fun createWritePendingIntent(uris: List<Uri>): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(contentResolver, uris)
        } else {
            null
        }
    }

    suspend fun moveMedia(item: MediaItem, targetRelativePath: String): Boolean = withContext(Dispatchers.IO) {
        try {
            val sanitizedPath = if (targetRelativePath.endsWith("/")) targetRelativePath else "$targetRelativePath/"
            val values = ContentValues().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    put(MediaStore.MediaColumns.RELATIVE_PATH, sanitizedPath)
                }
            }
            val rows = contentResolver.update(item.uri, values, null, null)
            rows > 0
        } catch (e: SecurityException) {
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
