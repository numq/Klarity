package playlist.presentation

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.numq.klarity.renderer.Renderer
import item.Item
import item.presentation.FailedItem
import item.presentation.LoadingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import playlist.Playlist
import playlist.SelectedPlaylistItem
import renderer.RendererRegistry

@Composable
fun PlaylistDrawer(
    modifier: Modifier,
    isVisible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    listState: LazyListState,
    rendererRegistry: RendererRegistry,
    playlist: Playlist,
    select: (Item) -> Unit,
    remove: (Item) -> Unit,
    content: @Composable () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(playlist.selectedPlaylistItem) {
        if (playlist.selectedPlaylistItem is SelectedPlaylistItem.Present) {
            val index = playlist.items.indexOfFirst { item -> item.id == playlist.selectedPlaylistItem.item.id }

            if (playlist.selectedPlaylistItem.item.id !in listState.layoutInfo.visibleItemsInfo.map(LazyListItemInfo::key)) {
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
                    shape = MaterialTheme.shapes.medium.copy(topStart = ZeroCornerSize, bottomStart = ZeroCornerSize)
                ) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        horizontalAlignment = Alignment.Start,
                        verticalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.Top),
                        state = listState
                    ) {
                        items(playlist.items, key = Item::id) { item ->
                            Box(
                                modifier = Modifier.fillMaxSize().animateItem(
                                    placementSpec = tween(easing = LinearEasing)
                                ), contentAlignment = Alignment.Center
                            ) {
                                when (item) {
                                    is Item.Failed -> FailedItem(item = item, remove = { remove(item) })

                                    is Item.Loading -> LoadingItem(item = item, remove = { remove(item) })

                                    is Item.Loaded -> {
                                        val renderer by produceState<Renderer?>(null, item.id) {
                                            value = withContext(Dispatchers.Default) {
                                                rendererRegistry.get(id = item.id).getOrNull()
                                            }
                                        }

                                        LoadedPlaylistItem(
                                            item = item,
                                            renderer = renderer,
                                            isSelected = (playlist.selectedPlaylistItem as? SelectedPlaylistItem.Present)?.item?.id == item.id,
                                            select = select,
                                            remove = remove
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