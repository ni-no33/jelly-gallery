package com.jellygallery.data.repository

import android.app.PendingIntent
import android.content.ContentResolver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.jellygallery.data.model.Album
import com.jellygallery.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

class MediaRepository(private val context: Context) {

    private val contentResolver: ContentResolver get() = context.contentResolver

    suspend fun getMediaList(albumPath: String? = null, onlyFavorites: Boolean = false): List<MediaItem> = withContext(Dispatchers.IO) {
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

        // 画像または動画
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
        val sortOrder = "${MediaStore.MediaColumns.DATE_TAKEN} DESC, ${MediaStore.MediaColumns.DATE_ADDED} DESC"

        try {
            contentResolver.query(uri, projection, selection, selectionArgs, sortOrder)?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val dataCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATA)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val mimeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.MIME_TYPE)
                val dateAddedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val dateTakenCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_TAKEN)
                val sizeCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.SIZE)
                val widthCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.WIDTH)
                val heightCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.HEIGHT)
                val bucketCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.BUCKET_DISPLAY_NAME)
                val favCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.MediaColumns.IS_FAVORITE)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val mime = cursor.getString(mimeCol) ?: ""
                    val isVideo = mime.startsWith("video/")
                    val contentUri = if (isVideo) {
                        ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
                    } else {
                        ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                    }

                    val isFavorite = if (favCol >= 0) cursor.getInt(favCol) == 1 else false
                    val albumName = cursor.getString(bucketCol) ?: "その他"

                    mediaList.add(
                        MediaItem(
                            id = id,
                            uri = contentUri,
                            path = cursor.getString(dataCol) ?: "",
                            displayName = cursor.getString(nameCol) ?: "",
                            mimeType = mime,
                            isVideo = isVideo,
                            dateAdded = cursor.getLong(dateAddedCol),
                            dateTaken = cursor.getLong(dateTakenCol),
                            size = cursor.getLong(sizeCol),
                            width = cursor.getInt(widthCol),
                            height = cursor.getInt(heightCol),
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

    suspend fun getAlbums(): List<Album> = withContext(Dispatchers.IO) {
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

        albumMap.map { (name, info) ->
            Album(
                id = name,
                name = name,
                relativePath = "Pictures/$name/",
                count = info.first,
                thumbnailUri = info.second
            )
        }.sortedByDescending { it.count }
    }

    /**
     * 削除要求用の PendingIntent を生成（Android 11+）
     */
    fun createDeletePendingIntent(uris: List<Uri>): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createDeleteRequest(contentResolver, uris)
        } else {
            null
        }
    }

    /**
     * お気に入り変更要求用の PendingIntent を生成（Android 11+）
     */
    fun createFavoritePendingIntent(uris: List<Uri>, isFavorite: Boolean): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createFavoriteRequest(contentResolver, uris, isFavorite)
        } else {
            null
        }
    }

    /**
     * ファイルの書き込み・変更要求用の PendingIntent を生成（Android 11+ で他アプリのファイルを移動する際に必須）
     */
    fun createWritePendingIntent(uris: List<Uri>): PendingIntent? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.createWriteRequest(contentResolver, uris)
        } else {
            null
        }
    }

    /**
     * ファイルを指定フォルダ（アルバム）に移動
     * relativePath 例: "Pictures/MyAlbum/" または "DCIM/Camera/"
     */
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
            // WriteRequest 認可が必要な場合
            throw e
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }
}
