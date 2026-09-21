package com.jellygallery.ui.screens.viewer

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jellygallery.data.model.Album
import com.jellygallery.data.model.MediaItem
import com.jellygallery.ui.screens.grid.AlbumBottomSheet
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    mediaList: List<MediaItem>,
    initialIndex: Int,
    albums: List<Album>,
    isTrashMode: Boolean = false,
    onBack: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onDeleteOrTrash: (MediaItem, onDone: () -> Unit) -> Unit,
    onRestore: (MediaItem, onDone: () -> Unit) -> Unit = { _, _ -> },
    onMove: (MediaItem, String, onDone: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    BackHandler {
        onBack()
    }

    val pageCount = mediaList.size
    if (pageCount == 0) {
        LaunchedEffect(Unit) {
            onBack()
        }
        return
    }

    val safeInitial = initialIndex.coerceIn(0, pageCount - 1)
    val pagerState = rememberPagerState(initialPage = safeInitial) { pageCount }

    var showControls by remember { mutableStateOf(true) }
    var showMoveSheet by remember { mutableStateOf(false) }

    val currentPage = pagerState.currentPage.coerceIn(0, pageCount - 1)
    val currentItem = mediaList.getOrNull(currentPage)

    fun advanceToNextAfterAction() {
        if (pageCount <= 1) {
            onBack()
        } else if (currentPage >= pageCount - 1) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(currentPage - 1)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
            key = { index -> if (index in mediaList.indices) mediaList[index].id else index }
        ) { page ->
            if (page in mediaList.indices) {
                val item = mediaList[page]

                if (item.isVideo) {
                    VideoPlayer(videoUri = item.uri)
                } else {
                    var scale by remember { mutableFloatStateOf(1f) }
                    var offsetX by remember { mutableFloatStateOf(0f) }
                    var offsetY by remember { mutableFloatStateOf(0f) }
                    var pullToDismissY by remember { mutableFloatStateOf(0f) }

                    val transformState = rememberTransformableState { zoomChange, panChange, _ ->
                        scale = (scale * zoomChange).coerceIn(1f, 4f)
                        if (scale > 1.05f) {
                            offsetX += panChange.x
                            offsetY += panChange.y
                        } else {
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .offset { IntOffset(0, pullToDismissY.roundToInt()) }
                            // 1. タップ・ダブルタップ検出
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        showControls = !showControls
                                    },
                                    onDoubleTap = {
                                        if (scale > 1.2f) {
                                            scale = 1f
                                            offsetX = 0f
                                            offsetY = 0f
                                        } else {
                                            scale = 2.5f
                                        }
                                    }
                                )
                            }
                            // 2. 下スワイプで戻る（Pull-to-Dismiss）検出（等倍時のみ）
                            .pointerInput(scale) {
                                if (scale <= 1.05f) {
                                    detectVerticalDragGestures(
                                        onDragEnd = {
                                            if (pullToDismissY > 120f) {
                                                onBack()
                                            } else {
                                                pullToDismissY = 0f
                                            }
                                        },
                                        onDragCancel = {
                                            pullToDismissY = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            if (pullToDismissY > 0 || dragAmount > 0) {
                                                change.consume()
                                                pullToDismissY = (pullToDismissY + dragAmount).coerceAtLeast(0f)
                                            }
                                        }
                                    )
                                }
                            }
                            // 3. 拡大時のみピンチ＆パン
                            .then(
                                if (scale > 1.05f) {
                                    Modifier.transformable(transformState)
                                } else {
                                    Modifier
                                }
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        AsyncImage(
                            model = item.uri,
                            contentDescription = item.displayName,
                            modifier = Modifier
                                .fillMaxSize()
                                .graphicsLayer(
                                    scaleX = scale,
                                    scaleY = scale,
                                    translationX = offsetX,
                                    translationY = offsetY
                                ),
                            contentScale = ContentScale.Fit
                        )
                    }
                }
            }
        }

        // 上部バー
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(),
            exit = fadeOut() + slideOutVertically(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = currentItem?.displayName ?: "",
                            style = MaterialTheme.typography.titleMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "${currentPage + 1} / $pageCount",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "戻る")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.65f),
                    titleContentColor = Color.White,
                    navigationIconContentColor = Color.White
                )
            )
        }

        // 下部アクションバー
        AnimatedVisibility(
            visible = showControls,
            enter = fadeIn() + slideInVertically(initialOffsetY = { it }),
            exit = fadeOut() + slideOutVertically(targetOffsetY = { it }),
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                color = Color.Black.copy(alpha = 0.75f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isTrashMode) {
                        // ゴミ箱表示時: 元に戻す（復元）
                        IconButton(
                            onClick = {
                                currentItem?.let { item ->
                                    onRestore(item) {
                                        advanceToNextAfterAction()
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Restore,
                                contentDescription = "元に戻す",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // ゴミ箱表示時: 完全に削除（ループ防止）
                        IconButton(
                            onClick = {
                                currentItem?.let { item ->
                                    onDeleteOrTrash(item) {
                                        advanceToNextAfterAction()
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.DeleteForever,
                                contentDescription = "完全に削除",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    } else {
                        // 通常表示時: お気に入り
                        IconButton(
                            onClick = { currentItem?.let { onToggleFavorite(it) } },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (currentItem?.isFavorite == true) Icons.Default.Star else Icons.Default.StarBorder,
                                contentDescription = "お気に入り",
                                tint = if (currentItem?.isFavorite == true) MaterialTheme.colorScheme.primary else Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // 移動
                        IconButton(
                            onClick = { showMoveSheet = true },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.DriveFileMove,
                                contentDescription = "移動",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // 共有
                        IconButton(
                            onClick = {
                                currentItem?.let { item ->
                                    val sendIntent = Intent().apply {
                                        action = Intent.ACTION_SEND
                                        putExtra(Intent.EXTRA_STREAM, item.uri)
                                        type = item.mimeType
                                    }
                                    val shareIntent = Intent.createChooser(sendIntent, "メディアを共有")
                                    context.startActivity(shareIntent)
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Share,
                                contentDescription = "共有",
                                tint = Color.White,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // ゴミ箱へ
                        IconButton(
                            onClick = {
                                currentItem?.let { item ->
                                    onDeleteOrTrash(item) {
                                        advanceToNextAfterAction()
                                    }
                                }
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "ゴミ箱へ",
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(28.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    if (showMoveSheet && currentItem != null) {
        AlbumBottomSheet(
            albums = albums,
            selectedAlbum = null,
            isMoveMode = true,
            onAlbumSelected = { album ->
                if (album != null) {
                    onMove(currentItem, album.name) {
                        advanceToNextAfterAction()
                    }
                    showMoveSheet = false
                }
            },
            onCreateNewAlbum = { newName ->
                onMove(currentItem, newName) {
                    advanceToNextAfterAction()
                }
                showMoveSheet = false
            },
            onDismiss = { showMoveSheet = false }
        )
    }
}
