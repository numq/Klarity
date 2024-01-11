package playlist.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.IconToggleButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.mapNotNull
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import media.Media
import playlist.Playlist
import kotlin.math.abs

@Composable
fun PlaylistDrawer(
    playlist: Playlist,
    widthFactor: Float = .5f,
    showOnStart: Boolean = false,
    upload: () -> Flow<String>,
    content: @Composable () -> Unit,
) = with(playlist) {
    val coroutineScope = rememberCoroutineScope { Dispatchers.Default }

    val shuffled by shuffled.collectAsState()

    val repeatMode by repeatMode.collectAsState()

    val queue by queue.collectAsState()

    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {

        val drawerWidth = (maxWidth * widthFactor).value

        val (drawerState, setDrawerState) = rememberSaveable {
            mutableStateOf(if (showOnStart) PlaylistDrawerState.OPENED else PlaylistDrawerState.CLOSED)
        }

        val animatedOffsetX = remember(drawerWidth) { Animatable(if (showOnStart) 0f else -drawerWidth) }

        val draggableState = rememberDraggableState { delta ->
            if (animatedOffsetX.value > -drawerWidth && animatedOffsetX.value < 0f) coroutineScope.launch {
                animatedOffsetX.animateTo(animatedOffsetX.value + delta)
            }
        }

        LaunchedEffect(drawerState) {
            withContext(Dispatchers.Default) {
                animatedOffsetX.animateTo(
                    when (drawerState) {
                        PlaylistDrawerState.OPENED -> 0f
                        PlaylistDrawerState.CLOSED -> -drawerWidth
                    }
                )
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .draggable(
                    draggableState,
                    Orientation.Horizontal,
                    onDragStopped = { velocity ->
                        if (abs(velocity) > drawerWidth / 2) {
                            when {
                                velocity > 0 -> setDrawerState(PlaylistDrawerState.OPENED)
                                velocity < 0 -> setDrawerState(PlaylistDrawerState.CLOSED)
                                else -> {
                                    if (animatedOffsetX.value < -drawerWidth / 2) setDrawerState(PlaylistDrawerState.OPENED)
                                    else setDrawerState(PlaylistDrawerState.CLOSED)
                                }
                            }
                        } else setDrawerState(PlaylistDrawerState.CLOSED)
                    }),
            contentAlignment = Alignment.TopStart
        ) {
            content()

            if (drawerState == PlaylistDrawerState.OPENED) Card(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(widthFactor)
//                    .widthIn(
//                        min = 256.dp,
//                        max = if (drawerState == PlaylistDrawerState.CLOSED) animatedWidth.value.dp else drawerWidth.dp
//                    )
                    .padding(top = 8.dp, bottom = 8.dp, end = 8.dp)
                    .graphicsLayer(translationX = animatedOffsetX.value),
//                    .graphicsLayer(translationX = animatedOffsetX),
                shape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.SpaceBetween
                ) {
                    PlaylistColumn(queue, modifier = Modifier.weight(1f)) { playlistMedia ->
                        PlaylistItem(
                            playlistMedia = playlistMedia,
                            play = {
                                coroutineScope.launch {
                                    select(playlistMedia)
                                    setDrawerState(PlaylistDrawerState.CLOSED)
                                }
                            },
                            remove = {
                                coroutineScope.launch {
                                    remove(playlistMedia)
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconToggleButton(
                            checked = shuffled,
                            onCheckedChange = {
                                coroutineScope.launch {
                                    toggleShuffle()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                Icons.Rounded.Shuffle,
                                "shuffle state",
                                modifier = Modifier.alpha(if (shuffled) 1f else .5f)
                            )
                        }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                changeRepeatMode(
                                    when (repeatMode) {
                                        Playlist.RepeatMode.NONE -> Playlist.RepeatMode.PLAYLIST

                                        Playlist.RepeatMode.PLAYLIST -> Playlist.RepeatMode.SINGLE

                                        Playlist.RepeatMode.SINGLE -> Playlist.RepeatMode.NONE
                                    }
                                )
                            }
                        }, modifier = Modifier.weight(1f)) {
                            when (repeatMode) {
                                Playlist.RepeatMode.NONE -> Icon(
                                    Icons.Rounded.Repeat, "repeat playlist once", modifier = Modifier.alpha(.5f)
                                )

                                Playlist.RepeatMode.PLAYLIST -> Icon(
                                    Icons.Rounded.Repeat, "repeat playlist repeatedly"
                                )

                                Playlist.RepeatMode.SINGLE -> Icon(
                                    Icons.Rounded.RepeatOne, "repeat single media"
                                )
                            }
                        }
                        IconButton(onClick = {
                            coroutineScope.launch {
                                upload()
                                    .mapNotNull(Media::create)
                                    .collect(::add)
                            }
                        }, modifier = Modifier.weight(1f)) {
                            Icon(Icons.Rounded.UploadFile, "upload files")
                        }
                    }
                }
            }
        }
    }
}