package com.jellygallery

import android.Manifest
import android.content.Intent
import android.content.pm.ActivityInfo
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jellygallery.data.model.AlbumSortOrder
import com.jellygallery.ui.screens.grid.MediaGridScreen
import com.jellygallery.ui.screens.viewer.MediaViewerScreen
import com.jellygallery.ui.theme.JellyGalleryTheme
import com.jellygallery.ui.viewmodel.GalleryViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JellyGalleryTheme {
                val uiState by viewModel.uiState.collectAsState()
                val coroutineScope = rememberCoroutineScope()
                val gridState = rememberLazyGridState()
                var viewingIndex by remember { mutableStateOf<Int?>(null) }

                // 権限要求ランチャー
                var hasPermission by remember { mutableStateOf(false) }
                val permissionsLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { permissions ->
                    val imagesGranted = permissions[Manifest.permission.READ_MEDIA_IMAGES] == true
                    val videosGranted = permissions[Manifest.permission.READ_MEDIA_VIDEO] == true
                    val visualSelected = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        permissions[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED] == true
                    } else false

                    hasPermission = imagesGranted || videosGranted || visualSelected
                    if (hasPermission) {
                        viewModel.checkPermissions()
                        viewModel.loadMedia()
                        viewModel.loadAlbums()
                    }
                }

                // 削除・移動・お気に入り時のシステム確認ダイアログ用ランチャー
                val intentSenderLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.StartIntentSenderForResult()
                ) { result ->
                    viewModel.onPendingIntentResult(result.resultCode == RESULT_OK)
                }

                LaunchedEffect(uiState.pendingIntent) {
                    uiState.pendingIntent?.let { pi ->
                        val request = IntentSenderRequest.Builder(pi.intentSender).build()
                        intentSenderLauncher.launch(request)
                    }
                }

                // 初回パーミッションチェック
                LaunchedEffect(Unit) {
                    val permissions = mutableListOf(
                        Manifest.permission.READ_MEDIA_IMAGES,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                    }
                    permissionsLauncher.launch(permissions.toTypedArray())
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!hasPermission && uiState.mediaList.isEmpty() && !uiState.isLoading) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "写真と動画へのアクセス",
                                style = MaterialTheme.typography.titleLarge
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                text = "写真・動画を表示・整理するためにメディアアクセス権限が必要です。",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(20.dp))
                            Button(
                                onClick = {
                                    val permissions = mutableListOf(
                                        Manifest.permission.READ_MEDIA_IMAGES,
                                        Manifest.permission.READ_MEDIA_VIDEO
                                    )
                                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                                        permissions.add(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED)
                                    }
                                    permissionsLauncher.launch(permissions.toTypedArray())
                                },
                                modifier = Modifier.height(48.dp)
                            ) {
                                Text("権限を許可する")
                            }
                        }
                    } else {
                        val currentViewing = viewingIndex
                        if (currentViewing != null && currentViewing in uiState.mediaList.indices) {
                            // ビューアー表示時: スマホの自動回転設定（ON/OFF）に従う
                            DisposableEffect(Unit) {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_USER
                                onDispose {
                                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }

                            MediaViewerScreen(
                                mediaList = uiState.mediaList,
                                initialIndex = currentViewing,
                                albums = uiState.albums,
                                isTrashMode = uiState.isTrashAlbum,
                                onBack = {
                                    currentViewing.let { idx ->
                                        coroutineScope.launch {
                                            gridState.scrollToItem(idx.coerceIn(0, (uiState.mediaList.size - 1).coerceAtLeast(0)))
                                        }
                                    }
                                    viewingIndex = null
                                },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onDeleteOrTrash = { item, onDone ->
                                    viewModel.deleteOrTrashItems(listOf(item), onDone)
                                },
                                onRestore = { item, onDone ->
                                    viewModel.restoreItems(listOf(item), onDone)
                                },
                                onMove = { item, albumName, onDone ->
                                    viewModel.moveItems(listOf(item), albumName, onDone)
                                }
                            )
                        } else {
                            DisposableEffect(Unit) {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                onDispose {}
                            }

                            MediaGridScreen(
                                uiState = uiState,
                                gridState = gridState,
                                onMediaClick = { clickedItem ->
                                    val index = uiState.mediaList.indexOf(clickedItem)
                                    if (index != -1) {
                                        viewingIndex = index
                                    }
                                },
                                onAlbumSelect = { album -> viewModel.selectAlbum(album) },
                                onFavoritesSelect = { viewModel.selectFavoritesAlbum() },
                                onTrashSelect = { viewModel.selectTrashAlbum() },
                                onToggleAlbumSort = {
                                    val next = if (uiState.albumSortOrder == AlbumSortOrder.COUNT_DESC) {
                                        AlbumSortOrder.NAME_ASC
                                    } else {
                                        AlbumSortOrder.COUNT_DESC
                                    }
                                    viewModel.setAlbumSortOrder(next)
                                },
                                onSetMediaSortOrder = { order ->
                                    viewModel.setMediaSortOrder(order)
                                },
                                onCreateNewAlbum = { newName ->
                                    viewModel.loadAlbums()
                                },
                                onToggleSelection = { viewModel.toggleSelection(it) },
                                onSelectAll = { viewModel.selectAll() },
                                onClearSelection = { viewModel.clearSelection() },
                                onShareSelected = {
                                    val items = uiState.selectedItems.toList()
                                    if (items.isNotEmpty()) {
                                        val uris = ArrayList<Uri>().apply {
                                            addAll(items.map { it.uri })
                                        }
                                        val sendIntent = Intent().apply {
                                            action = Intent.ACTION_SEND_MULTIPLE
                                            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
                                            type = "*/*"
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        val shareIntent = Intent.createChooser(sendIntent, "${uris.size}件のメディアを共有")
                                        startActivity(shareIntent)
                                    }
                                },
                                onDeleteSelected = {
                                    viewModel.deleteOrTrashItems(uiState.selectedItems.toList())
                                },
                                onRestoreSelected = {
                                    viewModel.restoreItems(uiState.selectedItems.toList())
                                },
                                onEmptyTrash = {
                                    viewModel.emptyTrash()
                                },
                                onMoveSelected = { targetAlbumName ->
                                    viewModel.moveItems(uiState.selectedItems.toList(), targetAlbumName)
                                },
                                onColumnsChange = { cols -> viewModel.setGridColumns(cols) }
                            )
                        }
                    }
                }
            }
        }
    }
}
