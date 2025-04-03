package hub

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.github.numq.klarity.compose.renderer.Foreground
import com.github.numq.klarity.compose.renderer.RendererComponent
import com.github.numq.klarity.compose.scale.ImageScale
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.media.Location
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.player.KlarityPlayer
import com.github.numq.klarity.core.state.PlayerState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import notification.Notification
import kotlin.time.Duration.Companion.microseconds

@Composable
fun UploadedHubItem(
    hubItem: HubItem.Uploaded,
    delete: () -> Unit,
    notify: (Notification) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val player = remember { KlarityPlayer.create().getOrThrow() }

    val settings by player.settings.collectAsState()

    val state by player.state.collectAsState()

    val renderer by player.renderer.collectAsState()

    val error by player.events.filterIsInstance<PlayerEvent.Error>().collectAsState(null)

    val playbackTimestamp by player.playbackTimestamp.collectAsState()

    val hoverInteractionSource = remember { MutableInteractionSource() }

    var previewJob by remember { mutableStateOf<Job?>(null) }

    var snapshotIndex by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        hoverInteractionSource.interactions.onEach { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> {
                    if (hubItem.snapshots.isNotEmpty()) {
                        previewJob?.cancel()
                        previewJob = coroutineScope.launch {
                            delay(500L)
                            while (isActive) {
                                snapshotIndex = (snapshotIndex + 1) % hubItem.snapshots.size
                                delay(200L)
                            }
                        }
                    }
                }

                else -> {
                    previewJob?.cancel()
                    previewJob = null
                    snapshotIndex = 0
                }
            }
        }.launchIn(coroutineScope)

        onDispose {
            previewJob?.cancel()
            previewJob = null

            coroutineScope.launch {
                player.close().getOrThrow()
            }
        }
    }

    LaunchedEffect(state) {
        player.changeSettings(settings.copy(playbackSpeedFactor = 1f))

        when (val currentState = state) {
            is PlayerState.Ready -> {
                if (currentState.media.location is Location.Remote) {
                    player.changeSettings(
                        settings = player.settings.value.copy(
                            audioBufferSize = 100,
                            videoBufferSize = 100
                        )
                    )
                }

                when (currentState) {
                    is PlayerState.Ready.Paused, is PlayerState.Ready.Stopped, is PlayerState.Ready.Completed -> {
                        if (currentState is PlayerState.Ready.Completed) {
                            player.stop()
                        }

                        hoverInteractionSource.tryEmit(HoverInteraction.Enter())
                    }

                    else -> Unit
                }
            }

            else -> Unit
        }
    }

    LaunchedEffect(error) {
        error?.run {
            notify(Notification(exception.localizedMessage))
        }
    }

    BoxWithConstraints(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
        Card {
            Box(
                modifier = Modifier.fillMaxSize().aspectRatio(1f).hoverable(
                    interactionSource = hoverInteractionSource, enabled = state !is PlayerState.Ready.Playing
                ).pointerInput(maxWidth, maxHeight, state) {
                    detectTapGestures(
                        onTap = {
                            coroutineScope.launch {
                                with(player) {
                                    when (state) {
                                        is PlayerState.Empty -> {
                                            player.prepare(
                                                location = hubItem.media.location.path,
                                                enableAudio = true,
                                                enableVideo = true
                                            )
                                            play()
                                        }

                                        is PlayerState.Ready.Playing -> {
                                            stop()
                                        }

                                        is PlayerState.Ready.Stopped -> play()

                                        is PlayerState.Ready.Completed -> {
                                            stop()
                                            play()
                                        }

                                        else -> Unit
                                    }
                                }
                            }
                        },
                        onPress = {
                            awaitRelease()
                            player.changeSettings(settings.copy(playbackSpeedFactor = 1f))
                        },
                        onLongPress = { (x, y) ->
                            if (state is PlayerState.Ready.Playing) {
                                val playbackSpeedFactor =
                                    if (x in 0f..size.width / 2f && y in 0f..size.height.toFloat()) 0.5f else 2f
                                coroutineScope.launch {
                                    player.changeSettings(settings.copy(playbackSpeedFactor = playbackSpeedFactor))
                                }
                            } else {
                                delete()
                            }
                        }
                    )
                }, contentAlignment = Alignment.Center
            ) {
                when (hubItem.media) {
                    is Media.Audio -> Icon(Icons.Default.AudioFile, null)

                    else -> when {
                        state is PlayerState.Ready.Playing -> RendererComponent(
                            modifier = Modifier.fillMaxSize(),
                            foreground = renderer?.run renderer@{
                                Foreground.Source(
                                    renderer = this@renderer,
                                    scale = ImageScale.Crop
                                )
                            } ?: Foreground.Empty
                        ) {
                            Icon(Icons.Default.BrokenImage, null)
                        }

                        else -> hubItem.snapshots.getOrNull(snapshotIndex)?.let { frame ->
                            RendererComponent(
                                modifier = Modifier.fillMaxSize(),
                                foreground = Foreground.Frame(frame = frame, scale = ImageScale.Crop)
                            ) {
                                Icon(Icons.Default.BrokenImage, null)
                            }
                        }
                    }
                }
                if (state is PlayerState.Ready.Playing) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(8.dp),
                        contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            "${playbackTimestamp.micros.microseconds}",
                            modifier = Modifier.padding(8.dp),
                            style = TextStyle(
                                drawStyle = Stroke(
                                    miter = 2f,
                                    width = 1f,
                                    join = StrokeJoin.Round
                                )
                            )
                        )
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
        }
    }
}