package io.github.numq.example.hub.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
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
import io.github.numq.example.item.presentation.FailedItem
import io.github.numq.example.item.presentation.LoadingItem
import io.github.numq.example.notification.NotificationError
import io.github.numq.example.notification.queue.rememberNotificationQueue
import io.github.numq.example.remote.RemoteUploadingDialog
import io.github.numq.example.renderer.RendererRegistry
import io.github.numq.example.slider.StepSlider
import io.github.numq.example.upload.UploadDialog
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class, ExperimentalMaterial3Api::class)
@Composable
fun HubView(feature: HubFeature, gridState: LazyGridState, rendererRegistry: RendererRegistry) {
    val coroutineScope = rememberCoroutineScope()

    val notificationQueue = rememberNotificationQueue()

    val state by feature.state.collectAsState()

    val thumbnailRenderers by rendererRegistry.renderers.map { renderers ->
        renderers.map { (id, registeredRenderer) ->
            id to registeredRenderer.renderer
        }.toMap()
    }.collectAsState(emptyMap())

    val error by feature.events.filterIsInstance(HubEvent.Error::class).collectAsState(null)

    LaunchedEffect(error) {
        error?.run {
            notificationQueue.push(message = message, label = Icons.Default.ErrorOutline)
        }
    }

    UploadDialog(isUploading = state.isFileChooserVisible, onMediaUploaded = { location ->
        coroutineScope.launch {
            feature.execute(command = HubCommand.AddToHub(location = location))
        }
    }, onClose = {
        coroutineScope.launch {
            feature.execute(command = HubCommand.Interaction.HideFileChooser)
        }
    })

    val dropTarget = remember {
        object : DragAndDropTarget {
            override fun onStarted(event: DragAndDropEvent) {
                super.onStarted(event)

                coroutineScope.launch {
                    feature.execute(HubCommand.Interaction.SetDragAndDropActive)
                }
            }

            override fun onEnded(event: DragAndDropEvent) {
                super.onEnded(event)

                coroutineScope.launch {
                    feature.execute(HubCommand.Interaction.SetDragAndDropInactive)
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
                        feature.execute(HubCommand.AddToHub(location))
                    }
                }

                return true
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize().dragAndDropTarget(shouldStartDragAndDrop = { event ->
            event.dragData() !is DragData.Image
        }, target = dropTarget).background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.BottomCenter
    ) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                TopAppBar(title = {
                    if (state.hub.items.isNotEmpty()) {
                        Text(
                            text = "Files: ${state.hub.items.count()}",
                            modifier = Modifier.padding(8.dp),
                            maxLines = 1,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }, actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            feature.execute(HubCommand.Interaction.ShowInputDialog)
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
                            feature.execute(HubCommand.Interaction.ShowFileChooser)
                        }
                    }) {
                        Icon(Icons.Default.UploadFile, null, tint = MaterialTheme.colorScheme.primary)
                    }
                })

                LazyVerticalGrid(
                    columns = GridCells.Fixed(state.sliderStep + 2),
                    modifier = Modifier.weight(1f).padding(4.dp),
                    state = gridState,
                    contentPadding = PaddingValues(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.Start),
                    verticalArrangement = Arrangement.spacedBy(4.dp, alignment = Alignment.Top),
                ) {
                    items(state.hub.items.toList(), key = Item::id) { item ->
                        Box(modifier = Modifier.fillMaxSize().aspectRatio(1f), contentAlignment = Alignment.Center) {
                            when (item) {
                                is Item.Loading -> LoadingItem(
                                    modifier = Modifier.fillMaxSize().aspectRatio(1f), item = item, remove = {
                                        coroutineScope.launch {
                                            feature.execute(HubCommand.RemoveFromHub(item))
                                        }
                                    })

                                is Item.Loaded -> LoadedHubItem(
                                    item = item,
                                    playbackItem = state.hub.playbackItem,
                                    playbackState = state.hub.playbackState,
                                    renderer = thumbnailRenderers[item.id],
                                    startPreview = {
                                        coroutineScope.launch {
                                            feature.execute(HubCommand.Preview.StartPreview(item = item))
                                        }
                                    },
                                    stopPreview = {
                                        coroutineScope.launch {
                                            feature.execute(HubCommand.Preview.StopPreview(item = item))
                                        }
                                    },
                                    startPlayback = {
                                        coroutineScope.launch {
                                            feature.execute(HubCommand.Playback.StartPlayback(item = item))
                                        }
                                    },
                                    stopPlayback = {
                                        coroutineScope.launch {
                                            feature.execute(HubCommand.Playback.StopPlayback(item = item))
                                        }
                                    },
                                    decreasePlaybackSpeed = {
                                        coroutineScope.launch {
                                            feature.execute(HubCommand.Playback.DecreasePlaybackSpeed)
                                        }
                                    },
                                    increasePlaybackSpeed = {
                                        coroutineScope.launch {
                                            feature.execute(HubCommand.Playback.IncreasePlaybackSpeed)
                                        }
                                    },
                                    resetPlaybackSpeed = {
                                        coroutineScope.launch {
                                            feature.execute(HubCommand.Playback.ResetPlaybackSpeed)
                                        }
                                    },
                                    remove = {
                                        coroutineScope.launch {
                                            feature.execute(HubCommand.RemoveFromHub(item))
                                        }
                                    })

                                is Item.Failed -> FailedItem(
                                    modifier = Modifier.fillMaxSize().aspectRatio(1f), item = item, remove = {
                                        coroutineScope.launch {
                                            feature.execute(HubCommand.RemoveFromHub(item))
                                        }
                                    })
                            }
                        }
                    }
                }

                HorizontalDivider(modifier = Modifier.fillMaxWidth())

                val sliderColor = MaterialTheme.colorScheme.onPrimaryContainer

                val pointColor = MaterialTheme.colorScheme.primaryContainer

                StepSlider(
                    steps = state.sliderSteps,
                    step = state.sliderStep,
                    sliderColor = sliderColor,
                    pointColor = pointColor,
                    onValueChange = { changedStep ->
                        coroutineScope.launch {
                            feature.execute(command = HubCommand.Interaction.SetSliderStep(step = changedStep))
                        }
                    })
            }
        }

        if (state.isDragAndDropActive) {
            Box(
                modifier = Modifier.fillMaxSize().background(color = MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Upload,
                    null,
                    modifier = Modifier.fillMaxSize(.25f),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        } else {
            RemoteUploadingDialog(isVisible = state.isInputDialogVisible, done = { address ->
                coroutineScope.launch {
                    feature.execute(HubCommand.AddToHub(address))

                    feature.execute(HubCommand.Interaction.HideInputDialog)
                }
            }, close = {
                coroutineScope.launch {
                    feature.execute(HubCommand.Interaction.HideInputDialog)
                }
            })
        }

        NotificationError(notificationQueue = notificationQueue)
    }
}