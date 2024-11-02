package playlist

import androidx.compose.animation.core.Animatable
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
import com.github.numq.klarity.compose.renderer.Renderer
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.media.Location
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.player.KlarityPlayer
import com.github.numq.klarity.core.preview.PreviewManager
import com.github.numq.klarity.core.preview.PreviewState
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
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import notification.Notification
import remote.RemoteUploadingDialog
import java.io.File
import kotlin.random.Random
import kotlin.time.Duration.Companion.microseconds

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PlaylistScreenSuccess(
    player: KlarityPlayer,
    previewManager: PreviewManager,
    openFileChooser: () -> List<File>,
    upload: (List<String>) -> List<File>,
    notify: (Notification) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val previewState by previewManager.state.collectAsState()

    val videoFormat = remember(previewState) {
        when (previewState) {
            is PreviewState.Empty -> null

            is PreviewState.Ready -> (previewState as PreviewState.Ready).media.format
        }
    }

    val settings by player.settings.collectAsState()

    val state by player.state.collectAsState()

    val renderer by player.renderer.collectAsState()

    val event by player.events.filterIsInstance<PlayerEvent.Error>().collectAsState(null)

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

    var isPlaylistVisible by remember { mutableStateOf(false) }

    var isRemoteUploadingDialogVisible by remember { mutableStateOf(false) }

    var uploadingJob by remember { mutableStateOf<Job?>(null) }

    val pendingChannel = remember { Channel<PlaylistItem.Pending>(Channel.UNLIMITED) }

    DisposableEffect(Unit) {
        uploadingJob = pendingChannel.consumeAsFlow().onEach { pendingItem ->
            coroutineScope.launch(Dispatchers.Default + Job()) {
                queue.add(pendingItem)
                ProbeManager.probe(pendingItem.location).mapCatching { media ->
                    val uploadedItem = PlaylistItem.Uploaded(
                        media = media,
                        snapshot = SnapshotManager.snapshot(media.location.value) { 0L }.getOrNull()
                    )
                    queue.replace(pendingItem, uploadedItem)
                }.onFailure {
                    queue.delete(pendingItem)
                }
            }
        }.launchIn(coroutineScope)
        onDispose {
            uploadingJob?.cancel()
            uploadingJob = null
            pendingChannel.close()
        }
    }

    LaunchedEffect(state) {
        player.changeSettings(settings.copy(playbackSpeedFactor = 1f))

        when (val currentState = state) {
            is PlayerState.Ready -> {
                if (currentState.media.location is Location.Remote) {
                    player.changeSettings(
                        settings = player.settings.value.copy(
                            audioBufferSize = 100, videoBufferSize = 100
                        )
                    )
                }

                if (currentState is PlayerState.Ready.Completed) {
                    if (queue.hasNext.value) queue.next()
                }
            }

            else -> Unit
        }
    }

    LaunchedEffect(event) {
        event?.run {
            notify(Notification(exception.localizedMessage))
        }
    }

    LaunchedEffect(selectedItem) {
        when (selectedItem) {
            is SelectedItem.Absent -> {
                previewManager.release()
                player.release()
            }

            is SelectedItem.Present<*> -> ((selectedItem as? SelectedItem.Present<*>)?.item as? PlaylistItem.Uploaded)?.run {
                when (val currentState = state) {
                    is PlayerState.Empty -> {
                        previewManager.prepare(location = media.location.value)
                        player.prepare(location = media.location.value, enableAudio = true, enableVideo = true)
                        player.play()
                    }

                    is PlayerState.Ready -> {
                        when (media.location.value) {
                            currentState.media.location.value -> when (state) {
                                is PlayerState.Ready.Playing,
                                is PlayerState.Ready.Paused,
                                is PlayerState.Ready.Completed,
                                is PlayerState.Ready.Seeking,
                                    -> {
                                    player.stop()
                                    player.play()
                                }

                                is PlayerState.Ready.Stopped -> player.play()

                                else -> Unit
                            }

                            else -> {
                                previewManager.release()
                                previewManager.prepare(location = media.location.value)
                                player.release()
                                player.prepare(
                                    location = media.location.value,
                                    enableAudio = true,
                                    enableVideo = true
                                )
                                player.play()
                            }
                        }
                    }
                }
            }
        }
    }

    fun addLocationToPlaylist(location: String) {
        val pendingItem = PlaylistItem.Pending(id = Random.nextLong(), location = location)
        coroutineScope.launch {
            pendingChannel.send(pendingItem)
        }
    }

    fun addFilesToPlaylist(files: List<File>) {
        files.map { it.absolutePath }.forEach(::addLocationToPlaylist)
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

                is PlaylistItem.Uploaded ->
                    queue.delete(playlistItem)
            }
        }
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        val playlistDrawerWidth = remember { Animatable(maxWidth.div(2).value) }

        LaunchedEffect(selectedItem) {
            if (selectedItem is SelectedItem.Absent) {
                playlistDrawerWidth.animateTo(maxWidth.div(2).value)
            }
        }

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
                        text = when (val location = location) {
                            is Location.Local -> location.fileName

                            is Location.Remote -> location.url
                        }, modifier = Modifier.padding(8.dp), maxLines = 1
                    )
                }
            }, navigationIcon = {
                IconButton(onClick = {
                    isPlaylistVisible = !isPlaylistVisible
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
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (val currentState = state) {
                    is PlayerState.Empty -> Box(modifier = Modifier.fillMaxSize())

                    is PlayerState.Ready -> {
                        val hoveredTimestamps = remember { MutableSharedFlow<HoveredTimestamp?>() }

                        Column(
                            modifier = Modifier.fillMaxSize(),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.SpaceBetween
                        ) {
                            Box(modifier = Modifier.weight(1f).pointerInput(state) {
                                detectTapGestures(onPress = {
                                    awaitRelease()

                                    player.changeSettings(settings.copy(playbackSpeedFactor = 1f))
                                }, onLongPress = { (x, y) ->
                                    if (state is PlayerState.Ready.Playing) {
                                        val playbackSpeedFactor = when {
                                            x in 0f..size.width / 2f && y in 0f..size.height.toFloat() -> 0.5f
                                            else -> 2f
                                        }

                                        coroutineScope.launch {
                                            player.changeSettings(settings.copy(playbackSpeedFactor = playbackSpeedFactor))
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
                                        when (currentState.media) {
                                            is Media.Audio -> Icon(Icons.Default.AudioFile, null)

                                            else -> Renderer(modifier = Modifier.fillMaxSize(),
                                                background = Background.Blur(),
                                                foreground = renderer?.let { renderer ->
                                                    Foreground.Source(renderer = renderer)
                                                } ?: Foreground.Empty) {
                                                Icon(Icons.Default.BrokenImage, null)
                                            }
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
                                                    }.padding(8.dp),
                                                    color = Color.White
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
                                            LaunchedEffect(selectedItem) {
                                                if (selectedItem is SelectedItem.Present<*>) {
                                                    playlistDrawerWidth.animateTo(maxWidth.value)
                                                }
                                            }
                                            Text(
                                                text = "${playbackTimestamp.millis.formatTimestamp()}/${currentState.media.durationMicros.microseconds.inWholeMilliseconds.formatTimestamp()}",
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
                                            shuffle = queue::shuffle,
                                            repeatMode = repeatMode,
                                            setRepeatMode = queue::setRepeatMode,
                                            hasPrevious = hasPrevious,
                                            hasNext = hasNext,
                                            previous = queue::previous,
                                            next = queue::next,
                                            play = player::play,
                                            pause = player::pause,
                                            resume = player::resume,
                                            stop = player::stop
                                        )
                                        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                            VolumeControls(
                                                modifier = Modifier.fillMaxWidth(),
                                                volume = settings.volume,
                                                isMuted = settings.isMuted,
                                                toggleMute = {
                                                    player.changeSettings(settings.copy(isMuted = !settings.isMuted))
                                                },
                                                changeVolume = { volume ->
                                                    player.changeSettings(settings.copy(volume = volume))
                                                }
                                            )
                                        }
                                    }
                                }

                                videoFormat?.let { format ->
                                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
                                        TimelinePreview(
                                            width = 128f,
                                            height = 128f,
                                            format = format,
                                            hoveredTimestamps = hoveredTimestamps,
                                            preview = { timestampMillis, width, height ->
                                                previewManager.preview(
                                                    timestampMillis = timestampMillis,
                                                    width = width,
                                                    height = height
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                            Timeline(
                                modifier = Modifier.fillMaxWidth().height(24.dp).padding(4.dp),
                                bufferTimestampMillis = bufferTimestamp.millis,
                                playbackTimestampMillis = playbackTimestamp.millis,
                                durationTimestampMillis = currentState.media.durationMicros.microseconds.inWholeMilliseconds,
                                seekTo = { timestampMillis ->
                                    player.seekTo(timestampMillis)
                                    player.resume()
                                },
                                onHoveredTimestamp = { value ->
                                    coroutineScope.launch {
                                        hoveredTimestamps.emit(value)
                                    }
                                }
                            )
                        }
                    }
                }
                PlaylistDrawer(
                    modifier = Modifier.fillMaxHeight().width(playlistDrawerWidth.value.dp),
                    listState = listState,
                    isPlaylistVisible = isPlaylistVisible,
                    items = items,
                    selectedItem = selectedItem,
                    select = { item ->
                        if (item is PlaylistItem.Uploaded) {
                            selectPlaylistItem(item)
                        }
                    },
                    delete = { item ->
                        deletePlaylistItem(item)
                    }
                )
            }
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