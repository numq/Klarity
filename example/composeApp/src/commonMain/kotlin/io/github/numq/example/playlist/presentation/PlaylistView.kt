package io.github.numq.example.playlist.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.FormatListBulleted
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.unit.dp
import io.github.numq.example.item.Item
import io.github.numq.example.notification.NotificationError
import io.github.numq.example.notification.queue.rememberNotificationQueue
import io.github.numq.example.playback.PlaybackState
import io.github.numq.example.playlist.SelectedPlaylistItem
import io.github.numq.example.remote.RemoteUploadingDialog
import io.github.numq.example.renderer.RendererRegistry
import io.github.numq.example.upload.UploadDialog
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PlaylistView(
    feature: PlaylistFeature,
    listState: LazyListState,
    isAnimationFinished: Boolean,
    rendererRegistry: RendererRegistry,
) {
    val coroutineScope = rememberCoroutineScope()

    val notificationQueue = rememberNotificationQueue()

    val state by feature.state.collectAsState()

    val thumbnailRenderers by rendererRegistry.renderers.map { renderers ->
        renderers.filterNot { (id, _) ->
            id == "playback" || id == "preview"
        }.map { (id, registeredRenderer) ->
            id to registeredRenderer.renderer
        }.toMap()
    }.collectAsState(emptyMap())

    val playbackRenderer by rendererRegistry.renderers.map { renderers ->
        renderers["playback"]?.renderer
    }.collectAsState(null)

    val previewRenderer by rendererRegistry.renderers.map { renderers ->
        renderers["preview"]?.renderer
    }.collectAsState(null)

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

                if (!state.isPlaylistVisible) {
                    coroutineScope.launch {
                        feature.execute(PlaylistCommand.Interaction.SetDragAndDropActive)
                    }
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
        }, target = dropTarget), contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            TopAppBar(title = {
                (state.playlist.playbackState as? PlaybackState.Ready)?.location?.let { location ->
                    Text(text = location, maxLines = 1, color = MaterialTheme.colorScheme.primary)
                }
            }, modifier = Modifier.fillMaxWidth(), navigationIcon = {
                IconButton(onClick = {
                    coroutineScope.launch {
                        feature.execute(command = if (state.isPlaylistVisible) PlaylistCommand.Interaction.HidePlaylist else PlaylistCommand.Interaction.ShowPlaylist)
                    }
                }) {
                    BadgedBox(badge = {
                        if (state.playlist.items.isNotEmpty()) {
                            Badge(containerColor = MaterialTheme.colorScheme.background) {
                                Text("${state.playlist.items.size}", color = MaterialTheme.colorScheme.primary)
                            }
                        }
                    }, content = {
                        Icon(
                            Icons.AutoMirrored.Filled.FormatListBulleted, null, tint = MaterialTheme.colorScheme.primary
                        )
                    })
                }
            }, actions = {
                IconButton(onClick = {
                    coroutineScope.launch {
                        feature.execute(PlaylistCommand.Interaction.ShowInputDialog)
                    }
                }) {
                    Icon(
                        if (state.isInputDialogVisible) Icons.Outlined.Public else Icons.Default.Public,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = {
                    coroutineScope.launch {
                        feature.execute(PlaylistCommand.Interaction.ShowFileChooser)
                    }
                }) {
                    Icon(Icons.Default.UploadFile, null, tint = MaterialTheme.colorScheme.primary)
                }
            })

            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                PlaylistDrawer(
                    modifier = Modifier.fillMaxSize(),
                    isEnabled = isAnimationFinished,
                    isVisible = state.isPlaylistVisible,
                    onVisibilityChange = { isVisible ->
                        coroutineScope.launch {
                            feature.execute(command = if (isVisible) PlaylistCommand.Interaction.ShowPlaylist else PlaylistCommand.Interaction.HidePlaylist)
                        }
                    },
                    listState = listState,
                    thumbnailRenderers = thumbnailRenderers,
                    dropTarget = dropTarget,
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
                        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            (state.playlist.selectedPlaylistItem as? SelectedPlaylistItem.Present)?.item?.let { item ->
                                when (val playbackState = state.playlist.playbackState) {
                                    is PlaybackState.Ready -> PlaylistPlayback(
                                        item = item,
                                        playbackState = playbackState,
                                        renderer = playbackRenderer,
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
                                                feature.execute(
                                                    PlaylistCommand.Preview.GetTimestamp(
                                                        previewTimestamp = previewTimestamp
                                                    )
                                                )
                                            }
                                        })

                                    is PlaybackState.Error -> Column(
                                        modifier = Modifier.padding(8.dp),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(
                                            space = 8.dp, alignment = Alignment.CenterVertically
                                        )
                                    ) {
                                        Text("Playback error occurred")
                                        Text(playbackState.exception.localizedMessage)
                                    }

                                    else -> Unit
                                }
                            }
                        }

                        if (state.isDragAndDropActive) {
                            Box(
                                modifier = Modifier.fillMaxSize()
                                    .background(color = MaterialTheme.colorScheme.primaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Upload,
                                    null,
                                    modifier = Modifier.fillMaxSize(.25f),
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    })
            }
        }

        if (!state.isDragAndDropActive) {
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