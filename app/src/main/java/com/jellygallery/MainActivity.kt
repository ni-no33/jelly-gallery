package com.jellygallery

import android.Manifest
import android.content.pm.ActivityInfo
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.jellygallery.ui.screens.grid.MediaGridScreen
import com.jellygallery.ui.screens.viewer.MediaViewerScreen
import com.jellygallery.ui.theme.JellyGalleryTheme
import com.jellygallery.ui.viewmodel.GalleryViewModel

class MainActivity : ComponentActivity() {

    private val viewModel: GalleryViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            JellyGalleryTheme {
                val uiState by viewModel.uiState.collectAsState()
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

                // pendingIntent が発行されたらシステムダイアログを起動
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
                        // 権限要求画面
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
                                text = "端末内の写真や動画を表示・整理するために権限が必要です。",
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
                            // ビューアー表示時：自動回転を許可
                            DisposableEffect(Unit) {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR
                                onDispose {
                                    requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                }
                            }

                            MediaViewerScreen(
                                mediaList = uiState.mediaList,
                                initialIndex = currentViewing,
                                albums = uiState.albums,
                                onBack = { viewingIndex = null },
                                onToggleFavorite = { viewModel.toggleFavorite(it) },
                                onDelete = { viewModel.deleteItems(listOf(it)) },
                                onMove = { item, albumName -> viewModel.moveItems(listOf(item), albumName) }
                            )
                        } else {
                            // 一覧画面表示時：縦向き固定（Jelly Star での使いやすさ重視）
                            DisposableEffect(Unit) {
                                requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                                onDispose {}
                            }

                            MediaGridScreen(
                                uiState = uiState,
                                onMediaClick = { clickedItem ->
                                    val index = uiState.mediaList.indexOf(clickedItem)
                                    if (index != -1) {
                                        viewingIndex = index
                                    }
                                },
                                onAlbumSelect = { album -> viewModel.selectAlbum(album) },
                                onCreateNewAlbum = { newName ->
                                    // 新規アルバムが作成されたらそのアルバムを選択
                                    viewModel.loadAlbums()
                                },
                                onToggleFavoriteFilter = { viewModel.toggleFavoritesFilter() },
                                onToggleSelection = { viewModel.toggleSelection(it) },
                                onClearSelection = { viewModel.clearSelection() },
                                onDeleteSelected = {
                                    viewModel.deleteItems(uiState.selectedItems.toList())
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
