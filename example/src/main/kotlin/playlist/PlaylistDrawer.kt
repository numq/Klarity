package playlist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.numq.klarity.queue.SelectedItem
import kotlinx.coroutines.launch

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDrawer(
    modifier: Modifier,
    isVisible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    listState: LazyListState,
    selectedItem: SelectedItem<PlaylistItem>,
    items: List<PlaylistItem>,
    select: (PlaylistItem) -> Unit,
    delete: (PlaylistItem) -> Unit,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(selectedItem) {
        ((selectedItem as? SelectedItem.Present<*>)?.item as? PlaylistItem.Uploaded)?.let { item ->
            val index = items.indexOf(item)
            if (item.media.id !in listState.layoutInfo.visibleItemsInfo.map(LazyListItemInfo::key)) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Surface(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val drawerWidth = remember(maxWidth) { maxWidth * .4f }

            val offsetX = remember { Animatable(if (isVisible) 0f else -drawerWidth.value) }

            LaunchedEffect(isVisible) {
                offsetX.animateTo(if (isVisible) 0f else -drawerWidth.value)
            }

            Box(modifier = Modifier.fillMaxSize().pointerInput(drawerWidth) {
                detectDragGestures(
                    onDragEnd = {
                        coroutineScope.launch {
                            val shouldOpen = offsetX.value > -drawerWidth.value * 0.5f
                            offsetX.animateTo(if (shouldOpen) 0f else -drawerWidth.value)
                            onVisibilityChange(shouldOpen)
                        }
                    },
                    onDragCancel = {
                        coroutineScope.launch {
                            val shouldOpen = offsetX.value > -drawerWidth.value * 0.5f
                            offsetX.animateTo(if (shouldOpen) 0f else -drawerWidth.value)
                            onVisibilityChange(shouldOpen)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        coroutineScope.launch {
                            offsetX.snapTo((offsetX.value + dragAmount.x).coerceIn(-drawerWidth.value, 0f))
                        }
                    },
                )
            }, contentAlignment = Alignment.CenterStart) {
                content()

                Card(
                    modifier = Modifier.fillMaxHeight().width(drawerWidth).padding(vertical = 8.dp)
                        .graphicsLayer(translationX = offsetX.value),
                    shape = MaterialTheme.shapes.medium.copy(
                        topStart = ZeroCornerSize,
                        bottomStart = ZeroCornerSize
                    )
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.Top),
                        state = listState
                    ) {
                        items(items, key = (PlaylistItem::id)) { item ->
                            Box(
                                modifier = Modifier.fillMaxSize().animateItemPlacement(
                                    animationSpec = tween(easing = LinearEasing)
                                ), contentAlignment = Alignment.Center
                            ) {
                                when (item) {
                                    is PlaylistItem.Pending -> PendingPlaylistItem(
                                        playlistItem = item,
                                        delete = { delete(item) })

                                    is PlaylistItem.Uploaded -> {
                                        val isSelected = remember(selectedItem) {
                                            when (selectedItem) {
                                                is SelectedItem.Absent -> false

                                                else -> ((selectedItem as? SelectedItem.Present<*>)?.item as? PlaylistItem.Uploaded)?.run {
                                                    media.id == item.media.id
                                                } ?: false
                                            }
                                        }

                                        UploadedPlaylistItem(
                                            playlistItem = item,
                                            isSelected = isSelected,
                                            select = { select(item) },
                                            delete = { delete(item) }
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
}