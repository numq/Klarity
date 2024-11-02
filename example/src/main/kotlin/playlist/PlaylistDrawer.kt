package playlist

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListItemInfo
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.material.Card
import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.github.numq.klarity.core.queue.SelectedItem

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PlaylistDrawer(
    modifier: Modifier,
    listState: LazyListState,
    isPlaylistVisible: Boolean,
    selectedItem: SelectedItem<PlaylistItem>,
    items: List<PlaylistItem>,
    select: (PlaylistItem) -> Unit,
    delete: (PlaylistItem) -> Unit,
) {
    LaunchedEffect(selectedItem) {
        ((selectedItem as? SelectedItem.Present<*>)?.item as? PlaylistItem.Uploaded)?.let { item ->
            val index = items.indexOf(item)
            if (item.media.id !in listState.layoutInfo.visibleItemsInfo.map(LazyListItemInfo::key)) {
                listState.animateScrollToItem(index)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.CenterStart) {
        AnimatedVisibility(
            visible = isPlaylistVisible,
            enter = slideInHorizontally { -it },
            exit = slideOutHorizontally { -it },
            modifier = modifier
        ) {
            Card(
                modifier = Modifier.padding(vertical = 4.dp),
                shape = MaterialTheme.shapes.medium.copy(topStart = ZeroCornerSize, bottomStart = ZeroCornerSize)
            ) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(8.dp),
                    horizontalAlignment = Alignment.Start,
                    verticalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.Top),
                    state = listState
                ) {
                    items(items, key = { item ->
                        when (item) {
                            is PlaylistItem.Pending -> item.id

                            is PlaylistItem.Uploaded -> item.media.id
                        }
                    }) { item ->
                        Box(
                            modifier = Modifier.fillMaxSize().animateItemPlacement(
                                animationSpec = tween(easing = LinearEasing)
                            ), contentAlignment = Alignment.Center
                        ) {
                            when (item) {
                                is PlaylistItem.Pending -> PendingPlaylistItem(playlistItem = item,
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