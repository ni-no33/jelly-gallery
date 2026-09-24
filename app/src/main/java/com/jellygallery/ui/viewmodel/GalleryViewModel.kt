package com.jellygallery.ui.viewmodel

import android.app.Application
import android.app.PendingIntent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jellygallery.data.model.Album
import com.jellygallery.data.model.MediaItem
import com.jellygallery.data.repository.MediaRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class GalleryUiState(
    val mediaList: List<MediaItem> = emptyList(),
    val albums: List<Album> = emptyList(),
    val selectedAlbum: Album? = null,
    val isFavoritesAlbum: Boolean = false,
    val isTrashAlbum: Boolean = false,
    val gridColumns: Int = 2,
    val selectedItems: Set<MediaItem> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = false,
    val pendingIntent: PendingIntent? = null,
    val hasManageStoragePermission: Boolean = false
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var onPendingSuccessAction: (suspend () -> Unit)? = null

    init {
        checkPermissions()
        loadMedia()
        loadAlbums()
    }

    fun checkPermissions() {
        _uiState.value = _uiState.value.copy(
            hasManageStoragePermission = repository.hasManageStoragePermission()
        )
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val list = repository.getMediaList(
                albumPath = if (!_uiState.value.isFavoritesAlbum && !_uiState.value.isTrashAlbum) _uiState.value.selectedAlbum?.name else null,
                onlyFavorites = _uiState.value.isFavoritesAlbum,
                includeTrashed = _uiState.value.isTrashAlbum
            )
            _uiState.value = _uiState.value.copy(mediaList = list, isLoading = false)
        }
    }

    fun loadAlbums() {
        viewModelScope.launch {
            val albums = repository.getAlbums()
            _uiState.value = _uiState.value.copy(albums = albums)
        }
    }

    fun selectAlbum(album: Album?) {
        _uiState.value = _uiState.value.copy(
            selectedAlbum = album,
            isFavoritesAlbum = false,
            isTrashAlbum = false
        )
        loadMedia()
    }

    fun selectFavoritesAlbum() {
        _uiState.value = _uiState.value.copy(
            selectedAlbum = null,
            isFavoritesAlbum = true,
            isTrashAlbum = false
        )
        loadMedia()
    }

    fun selectTrashAlbum() {
        _uiState.value = _uiState.value.copy(
            selectedAlbum = null,
            isFavoritesAlbum = false,
            isTrashAlbum = true
        )
        loadMedia()
    }

    fun setGridColumns(cols: Int) {
        val clamped = cols.coerceIn(1, 5)
        _uiState.value = _uiState.value.copy(gridColumns = clamped)
    }

    fun toggleSelection(item: MediaItem) {
        val current = _uiState.value.selectedItems.toMutableSet()
        if (current.contains(item)) {
            current.remove(item)
        } else {
            current.add(item)
        }
        _uiState.value = _uiState.value.copy(
            selectedItems = current,
            isSelectionMode = current.isNotEmpty()
        )
    }

    fun selectAll() {
        _uiState.value = _uiState.value.copy(
            selectedItems = _uiState.value.mediaList.toSet(),
            isSelectionMode = true
        )
    }

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedItems = emptySet(),
            isSelectionMode = false
        )
    }

    /**
     * 削除処理:
     * - 通常表示時: ゴミ箱へ移動（セーフティ）
     * - ゴミ箱表示時: 完全に削除（ループ防止）
     */
    fun deleteOrTrashItems(items: List<MediaItem>, onComplete: (() -> Unit)? = null) {
        if (items.isEmpty()) return

        if (_uiState.value.isTrashAlbum) {
            // ゴミ箱の中なら完全削除
            permanentDeleteItems(items, onComplete)
        } else {
            // 通常時はゴミ箱へ退避
            trashItems(items, onComplete)
        }
    }

    private fun trashItems(items: List<MediaItem>, onComplete: (() -> Unit)? = null) {
        if (repository.hasManageStoragePermission()) {
            viewModelScope.launch {
                for (item in items) {
                    repository.directDeleteOrTrash(item)
                }
                loadMedia()
                loadAlbums()
                clearSelection()
                onComplete?.invoke()
            }
        } else {
            val uris = items.map { it.uri }
            val pi = repository.createTrashPendingIntent(uris, true)
            if (pi != null) {
                onPendingSuccessAction = {
                    loadMedia()
                    loadAlbums()
                    clearSelection()
                    onComplete?.invoke()
                }
                _uiState.value = _uiState.value.copy(pendingIntent = pi)
            }
        }
    }

    /**
     * 完全削除（ゴミ箱から完全に消去）
     */
    fun permanentDeleteItems(items: List<MediaItem>, onComplete: (() -> Unit)? = null) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        val pi = repository.createDeletePendingIntent(uris)
        if (pi != null) {
            onPendingSuccessAction = {
                loadMedia()
                clearSelection()
                onComplete?.invoke()
            }
            _uiState.value = _uiState.value.copy(pendingIntent = pi)
        } else {
            viewModelScope.launch {
                for (item in items) {
                    repository.directDeleteOrTrash(item)
                }
                loadMedia()
                clearSelection()
                onComplete?.invoke()
            }
        }
    }

    /**
     * ゴミ箱から元に戻す（復元）
     */
    fun restoreItems(items: List<MediaItem>, onComplete: (() -> Unit)? = null) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        val pi = repository.createTrashPendingIntent(uris, false)
        if (pi != null) {
            onPendingSuccessAction = {
                loadMedia()
                clearSelection()
                onComplete?.invoke()
            }
            _uiState.value = _uiState.value.copy(pendingIntent = pi)
        }
    }

    /**
     * ゴミ箱を空にする（全完全削除）
     */
    fun emptyTrash(onComplete: (() -> Unit)? = null) {
        val allTrashed = _uiState.value.mediaList
        if (allTrashed.isNotEmpty()) {
            permanentDeleteItems(allTrashed, onComplete)
        }
    }

    fun toggleFavorite(item: MediaItem) {
        val targetState = !item.isFavorite
        val pi = repository.createFavoritePendingIntent(listOf(item.uri), targetState)
        if (pi != null) {
            onPendingSuccessAction = {
                loadMedia()
            }
            _uiState.value = _uiState.value.copy(pendingIntent = pi)
        }
    }

    fun moveItems(items: List<MediaItem>, targetAlbumName: String, onComplete: (() -> Unit)? = null) {
        if (items.isEmpty()) return
        val targetPath = "Pictures/$targetAlbumName/"
        val uris = items.map { it.uri }

        viewModelScope.launch {
            try {
                var successCount = 0
                for (item in items) {
                    if (repository.moveMedia(item, targetPath)) {
                        successCount++
                    }
                }
                if (successCount > 0) {
                    loadMedia()
                    loadAlbums()
                    clearSelection()
                    onComplete?.invoke()
                }
            } catch (e: SecurityException) {
                val pi = repository.createWritePendingIntent(uris)
                if (pi != null) {
                    onPendingSuccessAction = {
                        for (item in items) {
                            repository.moveMedia(item, targetPath)
                        }
                        loadMedia()
                        loadAlbums()
                        clearSelection()
                        onComplete?.invoke()
                    }
                    _uiState.value = _uiState.value.copy(pendingIntent = pi)
                }
            }
        }
    }

    fun onPendingIntentResult(isSuccess: Boolean) {
        _uiState.value = _uiState.value.copy(pendingIntent = null)
        if (isSuccess) {
            viewModelScope.launch {
                onPendingSuccessAction?.invoke()
                onPendingSuccessAction = null
            }
        } else {
            onPendingSuccessAction = null
        }
    }
}

