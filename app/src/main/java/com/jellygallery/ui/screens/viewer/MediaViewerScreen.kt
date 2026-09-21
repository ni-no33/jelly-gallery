package com.jellygallery.ui.screens.viewer

import android.content.Intent
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.rememberTransformableState
import androidx.compose.foundation.gestures.transformable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.jellygallery.data.model.Album
import com.jellygallery.data.model.MediaItem
import com.jellygallery.ui.screens.grid.AlbumBottomSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MediaViewerScreen(
    mediaList: List<MediaItem>,
    initialIndex: Int,
    albums: List<Album>,
    onBack: () -> Unit,
    onToggleFavorite: (MediaItem) -> Unit,
    onTrash: (MediaItem, onDone: () -> Unit) -> Unit,
    onMove: (MediaItem, String, onDone: () -> Unit) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // 戻るボタンでアプリが落ちず、一覧に戻る
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

    // 次の画像へスムーズに進むヘルパー
    fun advanceToNextAfterDelete() {
        if (pageCount <= 1) {
            onBack()
        } else if (currentPage >= pageCount - 1) {
            coroutineScope.launch {
                pagerState.animateScrollToPage(currentPage - 1)
            }
        }
        // それ以外は自動的に次のインデックスのアイテムが繰り上がる
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

                    // 拡大時のみパン・ドラッグを有効化し、等倍時は HorizontalPager にスワイプを完全に委ねる
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
                            .pointerInput(Unit) {
                                detectTapGestures(
                                    onTap = {
                                        showControls = !showControls
                                    },
                                    onDoubleTap = {
                                        // ダブルタップで一発拡大（2.5倍）↔ 等倍
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

        // 下部アクションバー（大型ボタン・親指サムゾーン）
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
                    // お気に入り
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

                    // ゴミ箱へ（削除後、即座に次の画像へスッと進む）
                    IconButton(
                        onClick = {
                            currentItem?.let { item ->
                                onTrash(item) {
                                    advanceToNextAfterDelete()
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

    // 移動先選択用ボトムシート
    if (showMoveSheet && currentItem != null) {
        AlbumBottomSheet(
            albums = albums,
            selectedAlbum = null,
            isMoveMode = true,
            onAlbumSelected = { album ->
                if (album != null) {
                    onMove(currentItem, album.name) {
                        advanceToNextAfterDelete()
                    }
                    showMoveSheet = false
                }
            },
            onCreateNewAlbum = { newName ->
                onMove(currentItem, newName) {
                    advanceToNextAfterDelete()
                }
                showMoveSheet = false
            },
            onDismiss = { showMoveSheet = false }
        )
    }
}
