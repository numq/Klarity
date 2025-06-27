package hub.presentation

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.draganddrop.dragAndDropTarget
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.outlined.Public
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draganddrop.DragAndDropEvent
import androidx.compose.ui.draganddrop.DragAndDropTarget
import androidx.compose.ui.draganddrop.DragData
import androidx.compose.ui.draganddrop.dragData
import androidx.compose.ui.unit.dp
import io.github.numq.klarity.renderer.Renderer
import item.Item
import item.presentation.FailedItem
import item.presentation.LoadingItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import notification.NotificationError
import notification.queue.rememberNotificationQueue
import remote.RemoteUploadingDialog
import renderer.RendererRegistry
import slider.StepSlider
import upload.UploadDialog
import java.io.File
import java.net.URI

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun HubView(feature: HubFeature, gridState: LazyGridState, rendererRegistry: RendererRegistry) {
    val coroutineScope = rememberCoroutineScope()

    val notificationQueue = rememberNotificationQueue()

    val state by feature.state.collectAsState()

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
        }, target = dropTarget).background(MaterialTheme.colors.background), contentAlignment = Alignment.BottomCenter
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
                            text = "Files: ${state.hub.items.count()}", modifier = Modifier.padding(8.dp), maxLines = 1
                        )
                    }
                }, actions = {
                    IconButton(onClick = {
                        coroutineScope.launch {
                            feature.execute(HubCommand.Interaction.ShowInputDialog)
                        }
                    }) {
                        Icon(if (state.isInputDialogVisible) Icons.Outlined.Public else Icons.Default.Public, null)
                    }
                    IconButton(onClick = {
                        coroutineScope.launch {
                            feature.execute(HubCommand.Interaction.ShowFileChooser)
                        }
                    }) {
                        Icon(Icons.Default.UploadFile, null)
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
                        Box(
                            modifier = Modifier.fillMaxSize().aspectRatio(1f).animateItem(
                                fadeInSpec = tween(), placementSpec = tween(), fadeOutSpec = tween()
                            ).animateContentSize(animationSpec = tween()), contentAlignment = Alignment.Center
                        ) {
                            when (item) {
                                is Item.Loading -> LoadingItem(item = item, remove = {
                                    coroutineScope.launch {
                                        feature.execute(HubCommand.RemoveFromHub(item))
                                    }
                                })

                                is Item.Loaded -> {
                                    val renderer by produceState<Renderer?>(null, item.id) {
                                        value = withContext(Dispatchers.Default) {
                                            rendererRegistry.get(item.id).getOrNull()
                                        }
                                    }

                                    LoadedHubItem(
                                        item = item,
                                        playbackItem = state.hub.playbackItem,
                                        playbackState = state.hub.playbackState,
                                        renderer = renderer,
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
                                }

                                is Item.Failed -> FailedItem(item = item, remove = {
                                    coroutineScope.launch {
                                        feature.execute(HubCommand.RemoveFromHub(item))
                                    }
                                })
                            }
                        }
                    }
                }

                Divider(modifier = Modifier.fillMaxWidth())

                val sliderColor by ButtonDefaults.buttonColors().contentColor(true)

                val pointColor by ButtonDefaults.buttonColors().backgroundColor(true)

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