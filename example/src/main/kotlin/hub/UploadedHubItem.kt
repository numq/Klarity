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
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.preview.PreviewManager
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.state.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import notification.Notification
import kotlin.time.Duration.Companion.microseconds

@Composable
fun UploadedHubItem(
    hubItem: HubItem.Uploaded,
    delete: () -> Unit,
    notify: (Notification) -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    val settings by hubItem.player.settings.collectAsState()

    val state by hubItem.player.state.collectAsState()

    val error by hubItem.player.events.filterIsInstance<PlayerEvent.Error>().collectAsState(null)

    val playbackTimestamp by hubItem.player.playbackTimestamp.collectAsState()

    val hoverInteractionSource = remember { MutableInteractionSource() }

    LaunchedEffect(state) {
        hubItem.player.changeSettings(settings.copy(playbackSpeedFactor = 1f)).getOrDefault(Unit)

        when (val currentState = state) {
            is PlayerState.Ready -> {
                when (currentState) {
                    is PlayerState.Ready.Paused, is PlayerState.Ready.Stopped, is PlayerState.Ready.Completed -> {
                        if (currentState is PlayerState.Ready.Completed) {
                            hubItem.player.stop().getOrDefault(Unit)
                        }
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
                                with(hubItem.player) {
                                    when (state) {
                                        is PlayerState.Empty -> play().getOrDefault(Unit)

                                        is PlayerState.Ready.Playing -> stop().getOrDefault(Unit)

                                        is PlayerState.Ready.Stopped -> play().getOrDefault(Unit)

                                        is PlayerState.Ready.Completed -> {
                                            stop().getOrDefault(Unit)
                                            play().getOrDefault(Unit)
                                        }

                                        else -> Unit
                                    }
                                }
                            }
                        },
                        onPress = {
                            awaitRelease()
                            hubItem.player.changeSettings(settings.copy(playbackSpeedFactor = 1f)).getOrDefault(Unit)
                        },
                        onLongPress = { (x, y) ->
                            if (state is PlayerState.Ready.Playing) {
                                val playbackSpeedFactor =
                                    if (x in 0f..size.width / 2f && y in 0f..size.height.toFloat()) 0.5f else 2f
                                coroutineScope.launch {
                                    hubItem.player.changeSettings(
                                        settings = settings.copy(playbackSpeedFactor = playbackSpeedFactor)
                                    ).getOrDefault(Unit)
                                }
                            } else {
                                delete()
                            }
                        }
                    )
                }, contentAlignment = Alignment.Center
            ) {
                when (val currentState = state) {
                    is PlayerState.Ready -> {
                        val format = remember(currentState) {
                            when (val media = currentState.media) {
                                is Media.Audio -> null

                                is Media.Video -> media.format

                                is Media.AudioVideo -> media.videoFormat
                            }
                        }

                        val previewManager = remember(currentState.media.location) {
                            runBlocking {
                                PreviewManager.create(location = currentState.media.location).getOrThrow()
                            }
                        }

                        DisposableEffect(currentState.media.location) {
                            onDispose {
                                runBlocking {
                                    previewManager.close().getOrThrow()
                                }
                            }
                        }

                        val playerRenderer = remember(format) {
                            format?.let(Renderer::create)?.getOrThrow()?.also(hubItem.player::attachRenderer)
                        }

                        DisposableEffect(format) {
                            onDispose {
                                playerRenderer?.close()
                            }
                        }

                        var snapshotJob by remember { mutableStateOf<Job?>(null) }

                        var snapshotIndex by remember { mutableStateOf(0) }

                        DisposableEffect(Unit) {
                            hoverInteractionSource.interactions.onEach { interaction ->
                                when (interaction) {
                                    is HoverInteraction.Enter -> {
                                        val n = 10

                                        snapshotJob?.cancel()
                                        snapshotJob = coroutineScope.launch {
                                            delay(500L)

                                            while (isActive) {
                                                if (state !is PlayerState.Ready.Playing) {
                                                    snapshotIndex = (snapshotIndex + 1) % n

                                                    previewManager.preview(
                                                        timestampMillis = (currentState.media.durationMicros.microseconds.inWholeMilliseconds * snapshotIndex) / (n - 1)
                                                    ).getOrThrow()
                                                }

                                                delay(200L)
                                            }
                                        }
                                    }

                                    else -> {
                                        snapshotJob?.cancel()
                                        snapshotJob = null

                                        snapshotIndex = 0
                                    }
                                }
                            }.launchIn(coroutineScope)

                            onDispose {
                                snapshotJob?.cancel()
                                snapshotJob = null

                                runBlocking {
                                    hubItem.player.release().getOrThrow()
                                }
                            }
                        }

                        when ((state as PlayerState.Ready).media) {
                            is Media.Audio -> Icon(Icons.Default.AudioFile, null)

                            else -> playerRenderer?.let {
                                RendererComponent(
                                    modifier = Modifier.fillMaxSize(),
                                    foreground = Foreground(
                                        renderer = it,
                                        imageScale = ImageScale.Crop
                                    )
                                )
                            } ?: Icon(Icons.Default.BrokenImage, null)
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

                    else -> Unit
                }
            }
        }
    }
}