package com.jellygallery.ui.screens.grid

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.jellygallery.data.model.Album
import com.jellygallery.data.model.MediaItem
import com.jellygallery.ui.viewmodel.GalleryUiState

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaGridScreen(
    uiState: GalleryUiState,
    gridState: LazyGridState,
    onMediaClick: (MediaItem) -> Unit,
    onAlbumSelect: (Album?) -> Unit,
    onFavoritesSelect: () -> Unit,
    onTrashSelect: () -> Unit,
    onCreateNewAlbum: (String) -> Unit,
    onToggleSelection: (MediaItem) -> Unit,
    onClearSelection: () -> Unit,
    onDeleteSelected: () -> Unit,
    onMoveSelected: (String) -> Unit,
    onColumnsChange: (Int) -> Unit
) {
    val configuration = LocalConfiguration.current
    val screenWidthDp = configuration.screenWidthDp

    LaunchedEffect(screenWidthDp) {
        if (uiState.gridColumns == 2 && screenWidthDp >= 400) {
            onColumnsChange(3)
        }
    }

    var showAlbumSheet by remember { mutableStateOf(false) }
    var showMoveSheet by remember { mutableStateOf(false) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    val transformableState = rememberTransformableState { zoomChange, _, _ ->
        zoomScale *= zoomChange
        if (zoomScale > 1.35f) {
            onColumnsChange(uiState.gridColumns - 1)
            zoomScale = 1f
        } else if (zoomScale < 0.75f) {
            onColumnsChange(uiState.gridColumns + 1)
            zoomScale = 1f
        }
    }

    val currentTitle = when {
        uiState.isFavoritesAlbum -> "⭐ お気に入り"
        uiState.isTrashAlbum -> "🗑️ ゴミ箱"
        uiState.selectedAlbum != null -> uiState.selectedAlbum.name
        else -> "すべてのメディア"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    if (uiState.isSelectionMode) {
                        Text("${uiState.selectedItems.size}件 選択中")
                    } else {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAlbumSheet = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = currentTitle,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = "アルバム選択")
                        }
                    }
                },
                navigationIcon = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = onClearSelection) {
                            Icon(Icons.Default.Close, contentDescription = "選択解除")
                        }
                    }
                },
                actions = {
                    if (uiState.isSelectionMode) {
                        IconButton(onClick = { showMoveSheet = true }) {
                            Icon(Icons.Default.DriveFileMove, contentDescription = "移動")
                        }
                        IconButton(onClick = onDeleteSelected) {
                            Icon(Icons.Default.Delete, contentDescription = "削除", tint = MaterialTheme.colorScheme.error)
                        }
                    } else {
                        IconButton(onClick = { showAlbumSheet = true }) {
                            Icon(Icons.Default.Folder, contentDescription = "アルバム一覧")
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .transformable(transformableState)
        ) {
            if (uiState.isLoading && uiState.mediaList.isEmpty()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (uiState.mediaList.isEmpty()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Default.PhotoLibrary,
                        contentDescription = null,
                        modifier = Modifier.size(64.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        when {
                            uiState.isFavoritesAlbum -> "お気に入りのメディアはありません"
                            uiState.isTrashAlbum -> "ゴミ箱は空です"
                            else -> "メディアが見つかりません"
                        },
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            } else {
                LazyVerticalGrid(
                    state = gridState,
                    columns = GridCells.Fixed(uiState.gridColumns),
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    items(
                        items = uiState.mediaList,
                        key = { it.id }
                    ) { item ->
                        val isSelected = uiState.selectedItems.contains(item)

                        Box(
                            modifier = Modifier
                                .aspectRatio(1f)
                                .clip(RoundedCornerShape(2.dp))
                                .combinedClickable(
                                    onClick = {
                                        if (uiState.isSelectionMode) {
                                            onToggleSelection(item)
                                        } else {
                                            onMediaClick(item)
                                        }
                                    },
                                    onLongClick = {
                                        onToggleSelection(item)
                                    }
                                )
                        ) {
                            AsyncImage(
                                model = item.uri,
                                contentDescription = item.displayName,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )

                            if (item.isVideo) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.BottomEnd)
                                        .padding(4.dp)
                                        .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 2.dp)
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Icon(
                                            Icons.Default.PlayArrow,
                                            contentDescription = null,
                                            tint = Color.White,
                                            modifier = Modifier.size(12.dp)
                                        )
                                        if (item.formattedDuration.isNotEmpty()) {
                                            Spacer(modifier = Modifier.width(2.dp))
                                            Text(
                                                text = item.formattedDuration,
                                                color = Color.White,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }

                            if (item.isFavorite) {
                                Icon(
                                    Icons.Default.Star,
                                    contentDescription = "お気に入り",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .align(Alignment.TopStart)
                                        .padding(4.dp)
                                        .size(16.dp)
                                )
                            }

                            if (uiState.isSelectionMode) {
                                Box(
                                    modifier = Modifier
                                        .fillMaxSize()
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.35f)
                                            else Color.Transparent
                                        )
                                )
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(4.dp)
                                        .size(22.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (isSelected) MaterialTheme.colorScheme.primary
                                            else Color.Black.copy(alpha = 0.5f)
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isSelected) {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = Color.Black,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (showAlbumSheet) {
        AlbumBottomSheet(
            albums = uiState.albums,
            selectedAlbum = uiState.selectedAlbum,
            isFavoritesSelected = uiState.isFavoritesAlbum,
            isTrashSelected = uiState.isTrashAlbum,
            isMoveMode = false,
            onAlbumSelected = onAlbumSelect,
            onFavoritesSelected = onFavoritesSelect,
            onTrashSelected = onTrashSelect,
            onCreateNewAlbum = onCreateNewAlbum,
            onDismiss = { showAlbumSheet = false }
        )
    }

    if (showMoveSheet) {
        AlbumBottomSheet(
            albums = uiState.albums,
            selectedAlbum = null,
            isMoveMode = true,
            onAlbumSelected = { targetAlbum ->
                if (targetAlbum != null) {
                    onMoveSelected(targetAlbum.name)
                    showMoveSheet = false
                }
            },
            onCreateNewAlbum = { newName ->
                onMoveSelected(newName)
                showMoveSheet = false
            },
            onDismiss = { showMoveSheet = false }
        )
    }
}
