package playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.rounded.Reorder
import androidx.compose.material.icons.rounded.UploadFile
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import controls.HoveredTimestamp
import controls.Timeline
import controls.VolumeControls
import extension.formatTimestamp
import io.github.numq.klarity.event.PlayerEvent
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.player.KlarityPlayer
import io.github.numq.klarity.preview.PreviewManager
import io.github.numq.klarity.probe.ProbeManager
import io.github.numq.klarity.queue.MediaQueue
import io.github.numq.klarity.queue.SelectedItem
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.renderer.compose.Background
import io.github.numq.klarity.renderer.compose.Foreground
import io.github.numq.klarity.renderer.compose.RendererComponent
import io.github.numq.klarity.snapshot.SnapshotManager
import io.github.numq.klarity.state.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import notification.Notification
import remote.RemoteUploadingDialog
import java.io.File
import kotlin.time.Duration.Companion.milliseconds

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistScreenSuccess(
    player: KlarityPlayer,
    openFileChooser: () -> List<File>,
    upload: (List<String>) -> List<File>,
    notify: (Notification) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val settings by player.settings.collectAsState()

    val state by player.state.collectAsState()

    val error by player.events.filterIsInstance<PlayerEvent.Error>().collectAsState(null)

    val bufferTimestamp by player.bufferTimestamp.collectAsState()

    val playbackTimestamp by player.playbackTimestamp.collectAsState()

    val listState = rememberLazyListState()

    val queue = remember { MediaQueue.create<PlaylistItem>() }

    val isShuffled by queue.isShuffled.collectAsState()

    val repeatMode by queue.repeatMode.collectAsState()

    val hasPrevious by queue.hasPrevious.collectAsState()

    val hasNext by queue.hasNext.collectAsState()

    val items by queue.items.collectAsState()

    val selectedItem by queue.selectedItem.collectAsState()

    var isDragAndDrop by remember { mutableStateOf(false) }

    var isPlaylistDrawerVisible by remember { mutableStateOf(false) }

    var isRemoteUploadingDialogVisible by remember { mutableStateOf(false) }

    var uploadingJob by remember { mutableStateOf<Job?>(null) }

    val pendingChannel = remember { Channel<PlaylistItem.Pending>(Channel.UNLIMITED) }

    fun handleException(throwable: Throwable) = notify(Notification(throwable.localizedMessage))

    DisposableEffect(Unit) {
        uploadingJob = pendingChannel.consumeAsFlow().onEach { pendingItem ->
            coroutineScope.launch(Dispatchers.Default) {
                queue.add(pendingItem)

                ProbeManager.probe(pendingItem.location).mapCatching { media ->
                    val renderer = media.videoFormat?.let(Renderer::create)?.getOrThrow()

                    val preview = SnapshotManager.snapshot(location = media.location).getOrThrow()

                    if (preview != null) {
                        renderer?.render(preview)
                    }

                    val uploadedItem = PlaylistItem.Uploaded(media = media, renderer = renderer, preview = preview)

                    queue.replace(pendingItem, uploadedItem)
                }.onFailure(::handleException).onFailure {
                    queue.delete(pendingItem)
                }
            }
        }.launchIn(coroutineScope)

        onDispose {
            uploadingJob?.cancel()
            uploadingJob = null

            pendingChannel.close()

            runBlocking {
                player.close().getOrThrow()

                items.filterIsInstance<PlaylistItem.Uploaded>().forEach { item ->
                    item.close()
                }
            }
        }
    }

    LaunchedEffect(state) {
        player.changeSettings(settings.copy(playbackSpeedFactor = 1f)).getOrThrow()

        if (state is PlayerState.Ready.Completed) {
            if (queue.hasNext.value) queue.next()
        }
    }

    LaunchedEffect(error) {
        error?.run {
            notify(Notification(exception.localizedMessage))
        }
    }

    LaunchedEffect(selectedItem) {
        when (selectedItem) {
            is SelectedItem.Absent -> player.release().getOrThrow()

            is SelectedItem.Present<*> -> ((selectedItem as? SelectedItem.Present<*>)?.item as? PlaylistItem.Uploaded)?.run {
                when (val currentState = state) {
                    is PlayerState.Empty -> {
                        player.prepare(location = media.location).getOrThrow()
                        player.play().getOrThrow()
                    }

                    is PlayerState.Ready -> {
                        when (media.location) {
                            currentState.media.location -> when (state) {
                                is PlayerState.Ready.Playing,
                                is PlayerState.Ready.Paused,
                                is PlayerState.Ready.Completed,
                                is PlayerState.Ready.Seeking,
                                    -> {
                                    player.stop().getOrThrow()
                                    player.play().getOrThrow()
                                }

                                is PlayerState.Ready.Stopped -> player.play().getOrThrow()

                                else -> Unit
                            }

                            else -> {
                                player.release().getOrThrow()
                                player.prepare(location = media.location).getOrThrow()
                                player.play().getOrThrow()
                            }
                        }
                    }
                }
            }
        }
    }

    fun addLocationToPlaylist(location: String) {
        val pendingItem = PlaylistItem.Pending(location = location)
        coroutineScope.launch {
            pendingChannel.send(pendingItem)
        }
    }

    fun addFilesToPlaylist(files: List<File>) {
        files.map(File::getAbsolutePath).forEach(::addLocationToPlaylist)
    }

    fun selectPlaylistItem(playlistItem: PlaylistItem.Uploaded) {
        coroutineScope.launch {
            if (((selectedItem as? SelectedItem.Present<*>)?.item as? PlaylistItem.Uploaded)?.media?.id == playlistItem.media.id) {
                queue.select(null)
            } else {
                queue.select(playlistItem)
            }
        }
    }

    fun deletePlaylistItem(playlistItem: PlaylistItem) {
        coroutineScope.launch {
            when (playlistItem) {
                is PlaylistItem.Pending -> queue.delete(playlistItem)

                is PlaylistItem.Uploaded -> {
                    queue.delete(playlistItem)

                    withContext(Dispatchers.IO) {
                        playlistItem.close()
                    }
                }
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(modifier = Modifier.fillMaxSize().onExternalDrag(onDragStart = {
            isDragAndDrop = true
        }, onDragExit = {
            isDragAndDrop = false
        }, onDrop = { externalDragValue ->
            isDragAndDrop = false
            when (val data = externalDragValue.dragData) {
                is DragData.FilesList -> addFilesToPlaylist(upload(data.readFiles()))

                is DragData.Text -> addLocationToPlaylist(data.readText())

                else -> Unit
            }
        })) {
            TopAppBar(title = {
                (state as? PlayerState.Ready)?.media?.run {
                    Text(
                        text = File(location).takeIf(File::exists)?.name ?: location,
                        modifier = Modifier.padding(8.dp),
                        maxLines = 1
                    )
                }
            }, navigationIcon = {
                IconButton(onClick = {
                    isPlaylistDrawerVisible = !isPlaylistDrawerVisible
                }) {
                    BadgedBox(badge = {
                        if (items.isNotEmpty()) {
                            Text("${items.size}", modifier = Modifier.padding(4.dp))
                        }
                    }, content = {
                        Icon(Icons.Rounded.Reorder, null)
                    })
                }
            }, actions = {
                IconButton(onClick = {
                    isRemoteUploadingDialogVisible = true
                }) {
                    if (isRemoteUploadingDialogVisible) {
                        Icon(Icons.Outlined.Public, null)
                    } else {
                        Icon(Icons.Default.Public, null)
                    }
                }
                IconButton(onClick = {
                    addFilesToPlaylist(openFileChooser())
                }) {
                    Icon(Icons.Rounded.UploadFile, null)
                }
            })
            PlaylistDrawer(modifier = Modifier.weight(1f), isVisible = isPlaylistDrawerVisible, onVisibilityChange = {
                isPlaylistDrawerVisible = it
            }, listState = listState, items = items, selectedItem = selectedItem, select = { item ->
                if (item is PlaylistItem.Uploaded) {
                    selectPlaylistItem(item)
                }
            }, delete = { item ->
                deletePlaylistItem(item)
            }, content = {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    when (val currentState = state) {
                        is PlayerState.Empty -> Box(modifier = Modifier.fillMaxSize())

                        is PlayerState.Ready -> {
                            val format = remember(currentState.media.videoFormat) {
                                currentState.media.videoFormat
                            }

                            val playerRenderer = remember(format) {
                                format?.let(Renderer::create)?.mapCatching { renderer ->
                                    runBlocking {
                                        SnapshotManager.snapshot(
                                            location = currentState.media.location
                                        ).getOrThrow()?.let { frame ->
                                            renderer.render(frame).getOrThrow()
                                        }
                                    }

                                    renderer
                                }?.getOrThrow()?.also(player::attachRenderer)
                            }

                            val previewManager = remember(currentState.media.location) {
                                PreviewManager.create(location = currentState.media.location).getOrNull()
                            }

                            val previewRenderer = remember(format) {
                                previewManager?.format?.let(Renderer::create)?.getOrNull()
                                    ?.also(previewManager::attachRenderer)
                            }

                            var hoveredTimestamp by remember {
                                mutableStateOf<HoveredTimestamp?>(null)
                            }

                            LaunchedEffect(hoveredTimestamp) {
                                hoveredTimestamp?.run {
                                    previewManager?.preview(
                                        timestamp = timestamp, debounceTime = 100.milliseconds
                                    )?.getOrThrow()
                                }
                            }

                            LaunchedEffect(state) {
                                if (state is PlayerState.Ready.Stopped) {
                                    ((selectedItem as? SelectedItem.Present)?.item as? PlaylistItem.Uploaded)?.run {
                                        preview?.let { frame ->
                                            playerRenderer?.render(frame)?.getOrThrow()
                                        }
                                    }
                                }
                            }

                            DisposableEffect(Unit) {
                                onDispose {
                                    runBlocking {
                                        playerRenderer?.close()?.getOrThrow()

                                        previewManager?.close()?.getOrThrow()

                                        previewRenderer?.close()?.getOrThrow()
                                    }
                                }
                            }

                            Column(
                                modifier = Modifier.fillMaxSize(),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.SpaceBetween
                            ) {
                                Box(modifier = Modifier.weight(1f).pointerInput(state) {
                                    detectTapGestures(onPress = {
                                        awaitRelease()

                                        player.changeSettings(settings.copy(playbackSpeedFactor = 1f)).getOrThrow()
                                    }, onLongPress = { (x, y) ->
                                        if (state is PlayerState.Ready.Playing) {
                                            val playbackSpeedFactor = when {
                                                x in 0f..size.width / 2f && y in 0f..size.height.toFloat() -> 0.5f
                                                else -> 2f
                                            }

                                            coroutineScope.launch {
                                                player.changeSettings(
                                                    settings.copy(playbackSpeedFactor = playbackSpeedFactor)
                                                ).getOrThrow()
                                            }
                                        }
                                    })
                                }, contentAlignment = Alignment.Center) {
                                    Column(
                                        modifier = Modifier.fillMaxSize(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                                            if (currentState.media is Media.Audio) {
                                                Icon(Icons.Default.AudioFile, null)
                                            } else {
                                                playerRenderer?.let {
                                                    RendererComponent(
                                                        modifier = Modifier.fillMaxSize(),
                                                        background = Background.Blur(),
                                                        foreground = Foreground(renderer = playerRenderer)
                                                    )
                                                } ?: Icon(Icons.Default.BrokenImage, null)
                                            }

                                            if (settings.playbackSpeedFactor != 1f) {
                                                Box(
                                                    modifier = Modifier.fillMaxSize().padding(8.dp),
                                                    contentAlignment = Alignment.Center
                                                ) {
                                                    Text(
                                                        "Playing on ${
                                                            settings.playbackSpeedFactor.toString().replace(".0", "")
                                                        }x speed", modifier = Modifier.drawBehind {
                                                            drawRoundRect(
                                                                color = Color.Black.copy(alpha = .5f),
                                                                cornerRadius = CornerRadius(16f, 16f)
                                                            )
                                                        }.padding(8.dp), color = Color.White
                                                    )
                                                }
                                            }
                                        }

                                        Row(
                                            modifier = Modifier.fillMaxWidth().padding(8.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            BoxWithConstraints(
                                                modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart
                                            ) {
                                                Text(
                                                    text = "${playbackTimestamp.inWholeMilliseconds.formatTimestamp()}/${currentState.media.duration.inWholeMilliseconds.formatTimestamp()}",
                                                    modifier = Modifier.padding(8.dp),
                                                    color = MaterialTheme.colors.primary
                                                )
                                            }
                                            PlaylistControls(
                                                modifier = Modifier.wrapContentSize(),
                                                color = MaterialTheme.colors.primary,
                                                state = state,
                                                playbackTimestamp = playbackTimestamp,
                                                isShuffled = isShuffled,
                                                shuffle = {
                                                    queue.shuffle().getOrThrow()
                                                },
                                                repeatMode = repeatMode,
                                                setRepeatMode = {
                                                    queue.setRepeatMode(it).getOrThrow()
                                                },
                                                hasPrevious = hasPrevious,
                                                hasNext = hasNext,
                                                previous = {
                                                    queue.previous().getOrThrow()
                                                },
                                                next = {
                                                    queue.next().getOrThrow()
                                                },
                                                play = {
                                                    player.play().getOrThrow()
                                                },
                                                pause = {
                                                    player.pause().getOrThrow()
                                                },
                                                resume = {
                                                    player.resume().getOrThrow()
                                                },
                                                stop = {
                                                    player.stop().getOrThrow()
                                                })
                                            Box(
                                                modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart
                                            ) {
                                                VolumeControls(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    volume = settings.volume,
                                                    isMuted = settings.isMuted,
                                                    toggleMute = {
                                                        player.changeSettings(
                                                            settings.copy(isMuted = !settings.isMuted)
                                                        ).getOrThrow()
                                                    },
                                                    changeVolume = { volume ->
                                                        player.changeSettings(
                                                            settings.copy(volume = volume)
                                                        ).getOrThrow()
                                                    })
                                            }
                                        }
                                    }

                                    hoveredTimestamp?.let { timestamp ->
                                        previewRenderer?.takeIf { !it.drawsNothing() }?.let {
                                            Box(
                                                modifier = Modifier.fillMaxSize(),
                                                contentAlignment = Alignment.BottomStart
                                            ) {
                                                TimelinePreview(
                                                    width = 128f,
                                                    height = 128f,
                                                    hoveredTimestamp = timestamp,
                                                    renderer = previewRenderer,
                                                )
                                            }
                                        }
                                    }
                                }

                                Timeline(
                                    modifier = Modifier.fillMaxWidth().height(24.dp).padding(4.dp),
                                    bufferTimestamp = bufferTimestamp,
                                    playbackTimestamp = playbackTimestamp,
                                    durationTimestamp = currentState.media.duration,
                                    seekTo = { timestamp ->
                                        player.seekTo(timestamp).getOrThrow()
                                        player.resume().getOrThrow()
                                    },
                                    onHoveredTimestamp = { value ->
                                        hoveredTimestamp = value
                                    })
                            }
                        }
                    }
                }
            })
        }
        if (isDragAndDrop) {
            LaunchedEffect(Unit) {
                isRemoteUploadingDialogVisible = false
            }

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
            RemoteUploadingDialog(isVisible = isRemoteUploadingDialogVisible, done = { address ->
                addLocationToPlaylist(address)
                isRemoteUploadingDialogVisible = false
            }, close = {
                isRemoteUploadingDialogVisible = false
            })
        }
    }
}