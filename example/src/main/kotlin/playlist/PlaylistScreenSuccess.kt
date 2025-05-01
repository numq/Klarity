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
import com.github.numq.klarity.compose.renderer.Background
import com.github.numq.klarity.compose.renderer.Foreground
import com.github.numq.klarity.compose.renderer.RendererComponent
import com.github.numq.klarity.compose.renderer.SkiaRenderer
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.player.KlarityPlayer
import com.github.numq.klarity.core.preview.PreviewManager
import com.github.numq.klarity.core.probe.ProbeManager
import com.github.numq.klarity.core.queue.MediaQueue
import com.github.numq.klarity.core.queue.SelectedItem
import com.github.numq.klarity.core.snapshot.SnapshotManager
import com.github.numq.klarity.core.state.PlayerState
import controls.HoveredTimestamp
import controls.Timeline
import controls.VolumeControls
import extension.formatTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
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
                    val renderer = media.videoFormat?.let(SkiaRenderer::create)?.mapCatching { renderer ->
                        SnapshotManager.snapshot(location = media.location, renderer = renderer).getOrThrow()

                        renderer
                    }?.mapCatching { renderer ->
                        renderer.withCache { cache ->
                            cache.firstOrNull()?.let { cachedFrame ->
                                renderer.render(cachedFrame).getOrThrow()
                            }
                        }

                        renderer
                    }?.getOrThrow()

                    val uploadedItem = PlaylistItem.Uploaded(media = media, renderer = renderer)

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
                    item.renderer?.close()?.getOrThrow()
                }
            }
        }
    }

    LaunchedEffect(state) {
        player.changeSettings(settings.copy(playbackSpeedFactor = 1f)).getOrDefault(Unit)

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
            is SelectedItem.Absent -> player.release().getOrDefault(Unit)

            is SelectedItem.Present<*> -> ((selectedItem as? SelectedItem.Present<*>)?.item as? PlaylistItem.Uploaded)?.run {
                when (val currentState = state) {
                    is PlayerState.Empty -> {
                        player.prepare(location = media.location).getOrDefault(Unit)
                        player.play().getOrDefault(Unit)
                    }

                    is PlayerState.Ready -> {
                        when (media.location) {
                            currentState.media.location -> when (state) {
                                is PlayerState.Ready.Playing,
                                is PlayerState.Ready.Paused,
                                is PlayerState.Ready.Completed,
                                is PlayerState.Ready.Seeking,
                                    -> {
                                    player.stop().getOrDefault(Unit)
                                    player.play().getOrDefault(Unit)
                                }

                                is PlayerState.Ready.Stopped -> player.play().getOrDefault(Unit)

                                else -> Unit
                            }

                            else -> {
                                player.release().getOrDefault(Unit)
                                player.prepare(location = media.location).getOrDefault(Unit)
                                player.play().getOrDefault(Unit)
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

                    playlistItem.renderer?.close()?.getOrThrow()
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
                                format?.let(SkiaRenderer::create)?.getOrThrow()?.also(player::attachRenderer)
                            }

                            val previewManager = remember(currentState.media.location) {
                                PreviewManager.create(location = currentState.media.location).getOrNull()
                            }

                            val previewRenderer = remember(format) {
                                previewManager?.format?.let(SkiaRenderer::create)?.getOrNull()
                                    ?.also(previewManager::attachRenderer)
                            }

                            var hoveredTimestamp by remember {
                                mutableStateOf<HoveredTimestamp?>(null)
                            }

                            LaunchedEffect(hoveredTimestamp) {
                                hoveredTimestamp?.run {
                                    previewManager?.preview(
                                        timestamp = timestamp, debounceTime = 100.milliseconds
                                    )?.getOrDefault(Unit)
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

                                        player.changeSettings(settings.copy(playbackSpeedFactor = 1f))
                                            .getOrDefault(Unit)
                                    }, onLongPress = { (x, y) ->
                                        if (state is PlayerState.Ready.Playing) {
                                            val playbackSpeedFactor = when {
                                                x in 0f..size.width / 2f && y in 0f..size.height.toFloat() -> 0.5f
                                                else -> 2f
                                            }

                                            coroutineScope.launch {
                                                player.changeSettings(settings.copy(playbackSpeedFactor = playbackSpeedFactor))
                                                    .getOrDefault(Unit)
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
                                                    queue.shuffle().getOrDefault(Unit)
                                                },
                                                repeatMode = repeatMode,
                                                setRepeatMode = {
                                                    queue.setRepeatMode(it).getOrDefault(Unit)
                                                },
                                                hasPrevious = hasPrevious,
                                                hasNext = hasNext,
                                                previous = {
                                                    queue.previous().getOrDefault(Unit)
                                                },
                                                next = {
                                                    queue.next().getOrDefault(Unit)
                                                },
                                                play = {
                                                    player.play().getOrDefault(Unit)
                                                },
                                                pause = {
                                                    player.pause().getOrDefault(Unit)
                                                },
                                                resume = {
                                                    player.resume().getOrDefault(Unit)
                                                },
                                                stop = {
                                                    player.stop().getOrDefault(Unit)
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
                                                        ).getOrDefault(Unit)
                                                    },
                                                    changeVolume = { volume ->
                                                        player.changeSettings(
                                                            settings.copy(volume = volume)
                                                        ).getOrDefault(Unit)
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
                                        player.seekTo(timestamp).getOrDefault(Unit)
                                        player.resume().getOrDefault(Unit)
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