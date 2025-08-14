package io.github.numq.example.playlist.presentation

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.numq.example.item.Item
import io.github.numq.example.item.presentation.FailedItem
import io.github.numq.example.item.presentation.LoadingItem
import io.github.numq.example.playlist.Playlist
import io.github.numq.example.playlist.SelectedPlaylistItem
import io.github.numq.klarity.renderer.Renderer

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistDrawer(
    modifier: Modifier,
    isEnabled: Boolean,
    isVisible: Boolean,
    onVisibilityChange: (Boolean) -> Unit,
    listState: LazyListState,
    thumbnailRenderers: Map<String, Renderer>,
    dropTarget: DragAndDropTarget,
    playlist: Playlist,
    select: (Item) -> Unit,
    remove: (Item) -> Unit,
    content: @Composable () -> Unit,
) {
    val drawerState = rememberDrawerState(DrawerValue.Closed)

    LaunchedEffect(isVisible) {
        when {
            isVisible -> drawerState.open()

            else -> drawerState.close()
        }
    }

    LaunchedEffect(drawerState.currentValue) {
        when (drawerState.currentValue) {
            DrawerValue.Closed -> onVisibilityChange(false)

            DrawerValue.Open -> onVisibilityChange(true)
        }
    }

    LaunchedEffect(playlist.selectedPlaylistItem) {
        if (playlist.selectedPlaylistItem is SelectedPlaylistItem.Present) {
            val index = playlist.items.indexOfFirst { item -> item.id == playlist.selectedPlaylistItem.item.id }

            if (playlist.selectedPlaylistItem.item.id !in listState.layoutInfo.visibleItemsInfo.map(LazyListItemInfo::key)) {
                listState.animateScrollToItem(index)
            }
        }
    }

    if (isEnabled) {
        ModalNavigationDrawer(
            drawerState = drawerState,
            modifier = modifier.dragAndDropTarget(shouldStartDragAndDrop = { event ->
                event.dragData() !is DragData.Image
            }, target = dropTarget),
            drawerContent = {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.Top),
                    state = listState
                ) {
                    if (playlist.items.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxSize().padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Playlist is empty",
                                    modifier = Modifier.padding(horizontal = 4.dp),
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                    items(playlist.items, key = Item::id) { item ->
                        Box(
                            modifier = Modifier.fillMaxSize()
                                .animateItem(placementSpec = tween(easing = LinearEasing)),
                            contentAlignment = Alignment.Center
                        ) {
                            when (item) {
                                is Item.Failed -> FailedItem(
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    item = item,
                                    remove = { remove(item) })

                                is Item.Loading -> LoadingItem(
                                    modifier = Modifier.fillMaxWidth().height(64.dp),
                                    item = item,
                                    remove = { remove(item) })

                                is Item.Loaded -> LoadedPlaylistItem(
                                    item = item,
                                    renderer = thumbnailRenderers[item.id],
                                    isSelected = (playlist.selectedPlaylistItem as? SelectedPlaylistItem.Present)?.item?.id == item.id,
                                    select = select,
                                    remove = remove
                                )
                            }
                        }
                    }
                }
            },
            content = content
        )
    }
}