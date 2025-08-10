package playlist.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import io.github.numq.klarity.renderer.Renderer
import item.Item
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notification.NotificationError
import notification.queue.rememberNotificationQueue
import playback.PlaybackState
import playlist.SelectedPlaylistItem
import remote.RemoteUploadingDialog
import renderer.RendererRegistry
import upload.UploadDialog
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistView(feature: PlaylistFeature, listState: LazyListState, rendererRegistry: RendererRegistry) {
    val coroutineScope = rememberCoroutineScope()

    val notificationQueue = rememberNotificationQueue()

    val state by feature.state.collectAsState()

    val error by feature.events.filterIsInstance(PlaylistEvent.Error::class).collectAsState(null)

    LaunchedEffect(error) {
        error?.run {
            notificationQueue.push(message = message, label = Icons.Default.ErrorOutline)
        }
    }

    UploadDialog(isUploading = state.isFileChooserVisible, onMediaUploaded = { location ->
        coroutineScope.launch {
            feature.execute(command = PlaylistCommand.AddToPlaylist(location = location))
        }
    }, onClose = {
        coroutineScope.launch {
            feature.execute(command = PlaylistCommand.Interaction.HideFileChooser)
        }
    })

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                super.onStarted(event)

                coroutineScope.launch {
                    feature.execute(PlaylistCommand.Interaction.SetDragAndDropActive)
                }
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEnded(event)

                coroutineScope.launch {
                    feature.execute(PlaylistCommand.Interaction.SetDragAndDropInactive)
                }
            }

            override fun onDrop(event: DragAndDropEvent): Boolean {
                val locations = when (val data = event.dragData()) {
                    is DragData.FilesList -> data.readFiles().map { file ->
                        File(URI(file)).absolutePath
                    }

                    else -> emptyList()
                }

                coroutineScope.launch {
                    locations.forEach { location ->
                        feature.execute(PlaylistCommand.AddToPlaylist(location))
                    }
                }

                return true
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { event ->
            event.dragData() !is DragData.Image
        }, target = dropTarget).background(MaterialTheme.colors.background), contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopAppBar(title = {
                (state.playlist.playbackState as? PlaybackState.Ready)?.location?.let { location ->
                    Text(text = location, maxLines = 1)
                }
            }, modifier = Modifier.fillMaxWidth(), navigationIcon = {
                IconButton(onClick = {
                    coroutineScope.launch {
                        feature.execute(command = if (state.isPlaylistVisible) PlaylistCommand.Interaction.HidePlaylist else PlaylistCommand.Interaction.ShowPlaylist)
                    }
                }) {
                    BadgedBox(badge = {
                        if (state.playlist.items.isNotEmpty()) {
                            Badge(backgroundColor = MaterialTheme.colors.background) {
                                Text("${state.playlist.items.size}")
                            }
                        }
                    }, content = {
                        Icon(Icons.Default.FeaturedPlayList, null)
                    })
                }
            }, actions = {
                IconButton(onClick = {
                    coroutineScope.launch {
                        feature.execute(PlaylistCommand.Interaction.ShowInputDialog)
                    }
                }) {
                    Icon(
                        if (state.isInputDialogVisible) Icons.Outlined.Public else Icons.Default.Public, null
                    )
                }
                IconButton(onClick = {
                    coroutineScope.launch {
                        feature.execute(PlaylistCommand.Interaction.ShowFileChooser)
                    }
                }) {
                    Icon(Icons.Default.UploadFile, null)
                }
            })

            PlaylistDrawer(
                modifier = Modifier.fillMaxSize(),
                isVisible = state.isPlaylistVisible,
                onVisibilityChange = { isVisible ->
                    coroutineScope.launch {
                        feature.execute(command = if (isVisible) PlaylistCommand.Interaction.ShowPlaylist else PlaylistCommand.Interaction.HidePlaylist)
                    }
                },
                listState = listState,
                rendererRegistry = rendererRegistry,
                playlist = state.playlist,
                select = { item ->
                    if (item is Item.Loaded) {
                        coroutineScope.launch {
                            feature.execute(command = PlaylistCommand.SelectItem(item = item))
                        }
                    }
                },
                remove = { item ->
                    coroutineScope.launch {
                        feature.execute(command = PlaylistCommand.RemoveFromPlaylist(item = item))
                    }
                },
                content = {
                    (state.playlist.selectedPlaylistItem as? SelectedPlaylistItem.Present)?.item?.let { item ->
                        (state.playlist.playbackState as? PlaybackState.Ready)?.let { playbackState ->
                            val playbackRenderer by produceState<Renderer?>(null, item, playbackState) {
                                value = withContext(Dispatchers.Default) {
                                    when (playbackState) {
                                        is PlaybackState.Ready.Stopped -> rendererRegistry.get(id = item.id)
                                            .getOrThrow()

                                        else -> rendererRegistry.get(id = "playback").getOrThrow()
                                    }
                                }
                            }

                            val previewRenderer by produceState<Renderer?>(null, item) {
                                value = withContext(Dispatchers.Default) {
                                    rendererRegistry.get(id = "preview").getOrNull()
                                }
                            }

                            PlaylistPlayback(
                                playbackState = playbackState,
                                playbackRenderer = playbackRenderer,
                                previewRenderer = previewRenderer,
                                previewTimestamp = state.previewTimestamp,
                                isOverlayVisible = state.isOverlayVisible,
                                showOverlay = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Interaction.ShowOverlay)
                                    }
                                },
                                hideOverlay = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Interaction.HideOverlay)
                                    }
                                },
                                isShuffled = state.playlist.isShuffled,
                                mode = state.playlist.mode,
                                hasPrevious = state.playlist.hasPrevious,
                                hasNext = state.playlist.hasNext,
                                shuffle = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Shuffle)
                                    }
                                },
                                setMode = { mode ->
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.SetMode(mode = mode))
                                    }
                                },
                                previous = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Previous)
                                    }
                                },
                                next = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Next)
                                    }
                                },
                                play = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Playback.Play(item = item))
                                    }
                                },
                                pause = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Playback.Pause(item = item))
                                    }
                                },
                                resume = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Playback.Resume(item = item))
                                    }
                                },
                                stop = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Playback.Stop(item = item))
                                    }
                                },
                                seekTo = { timestamp ->
                                    coroutineScope.launch {
                                        feature.execute(
                                            PlaylistCommand.Playback.SeekTo(
                                                item = item, timestamp = timestamp
                                            )
                                        )

                                        feature.execute(PlaylistCommand.Playback.Play(item = item))
                                    }
                                },
                                toggleMute = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Playback.ToggleMute)
                                    }
                                },
                                changeVolume = { updatedVolume ->
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Playback.ChangeVolume(volume = updatedVolume))
                                    }
                                },
                                decreasePlaybackSpeed = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Playback.DecreaseSpeed)
                                    }
                                },
                                increasePlaybackSpeed = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Playback.IncreaseSpeed)
                                    }
                                },
                                resetPlaybackSpeed = {
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Playback.ResetSpeed)
                                    }
                                },
                                onPreviewTimestamp = { previewTimestamp ->
                                    coroutineScope.launch {
                                        feature.execute(PlaylistCommand.Preview.GetTimestamp(previewTimestamp = previewTimestamp))
                                    }
                                })
                        }
                    }
                })
        }

        if (state.isDragAndDropActive) {
            Box(
                modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colors.primaryVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Upload,
                    null,
                    modifier = Modifier.fillMaxSize(.25f),
                    tint = MaterialTheme.colors.primary
                )
            }
        } else {
            RemoteUploadingDialog(isVisible = state.isInputDialogVisible, done = { address ->
                coroutineScope.launch {
                    feature.execute(PlaylistCommand.AddToPlaylist(address))

                    feature.execute(PlaylistCommand.Interaction.HideInputDialog)
                }
            }, close = {
                coroutineScope.launch {
                    feature.execute(PlaylistCommand.Interaction.HideInputDialog)
                }
            })
        }

        NotificationError(notificationQueue = notificationQueue)
    }
}