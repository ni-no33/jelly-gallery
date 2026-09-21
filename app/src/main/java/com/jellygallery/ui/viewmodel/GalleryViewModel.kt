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
    val onlyFavorites: Boolean = false,
    val gridColumns: Int = 2,
    val selectedItems: Set<MediaItem> = emptySet(),
    val isSelectionMode: Boolean = false,
    val isLoading: Boolean = false,
    val pendingIntent: PendingIntent? = null
)

class GalleryViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MediaRepository(application)

    private val _uiState = MutableStateFlow(GalleryUiState())
    val uiState: StateFlow<GalleryUiState> = _uiState.asStateFlow()

    private var pendingMoveAction: (suspend () -> Unit)? = null

    init {
        loadMedia()
        loadAlbums()
    }

    fun loadMedia() {
        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true)
            val list = repository.getMediaList(
                albumPath = _uiState.value.selectedAlbum?.name,
                onlyFavorites = _uiState.value.onlyFavorites
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
        _uiState.value = _uiState.value.copy(selectedAlbum = album)
        loadMedia()
    }

    fun toggleFavoritesFilter() {
        val next = !_uiState.value.onlyFavorites
        _uiState.value = _uiState.value.copy(onlyFavorites = next)
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

    fun clearSelection() {
        _uiState.value = _uiState.value.copy(
            selectedItems = emptySet(),
            isSelectionMode = false
        )
    }

    fun deleteItems(items: List<MediaItem>) {
        if (items.isEmpty()) return
        val uris = items.map { it.uri }
        val pi = repository.createDeletePendingIntent(uris)
        if (pi != null) {
            _uiState.value = _uiState.value.copy(pendingIntent = pi)
        }
    }

    fun toggleFavorite(item: MediaItem) {
        val targetState = !item.isFavorite
        val pi = repository.createFavoritePendingIntent(listOf(item.uri), targetState)
        if (pi != null) {
            _uiState.value = _uiState.value.copy(pendingIntent = pi)
        }
    }

    fun moveItems(items: List<MediaItem>, targetAlbumName: String) {
        if (items.isEmpty()) return
        val targetPath = "Pictures/$targetAlbumName/"
        val uris = items.map { it.uri }

        viewModelScope.launch {
            try {
                // まず直接移動を試みる
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
                }
            } catch (e: SecurityException) {
                // 書き込み権限が必要な場合、システムダイアログを表示
                val pi = repository.createWritePendingIntent(uris)
                if (pi != null) {
                    pendingMoveAction = {
                        for (item in items) {
                            repository.moveMedia(item, targetPath)
                        }
                        loadMedia()
                        loadAlbums()
                        clearSelection()
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
                pendingMoveAction?.invoke()
                pendingMoveAction = null
                loadMedia()
                loadAlbums()
                clearSelection()
            }
        } else {
            pendingMoveAction = null
        }
    }
}
