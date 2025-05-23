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
import io.github.numq.klarity.event.PlayerEvent
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.renderer.compose.Foreground
import io.github.numq.klarity.renderer.compose.ImageScale
import io.github.numq.klarity.renderer.compose.RendererComponent
import io.github.numq.klarity.state.PlayerState
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import notification.Notification

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

    var snapshotIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(Unit) {
        hoverInteractionSource.interactions.collectLatest { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> if (state is PlayerState.Ready.Stopped) {
                    while (isActive) {
                        snapshotIndex = (snapshotIndex + 1) % hubItem.snapshots.size

                        delay(500L)
                    }
                }

                is HoverInteraction.Exit -> {
                    snapshotIndex = 0
                }
            }
        }
    }

    LaunchedEffect(state) {
        hubItem.player.changeSettings(settings.copy(playbackSpeedFactor = 1f)).getOrThrow()

        when (state) {
            is PlayerState.Ready.Playing -> {
                snapshotIndex = 0
            }

            is PlayerState.Ready.Completed -> hubItem.player.stop().getOrThrow()

            else -> Unit
        }
    }

    LaunchedEffect(snapshotIndex) {
        if (state !is PlayerState.Ready.Playing) {
            hubItem.snapshots.getOrNull(snapshotIndex)?.let { frame ->
                hubItem.renderer?.render(frame)?.getOrThrow()
            }
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
                    detectTapGestures(onTap = {
                        coroutineScope.launch {
                            with(hubItem.player) {
                                when (state) {
                                    is PlayerState.Empty -> play().getOrThrow()

                                    is PlayerState.Ready.Playing -> stop().getOrThrow()

                                    is PlayerState.Ready.Stopped -> play().getOrThrow()

                                    is PlayerState.Ready.Completed -> {
                                        stop().getOrThrow()
                                        play().getOrThrow()
                                    }

                                    else -> Unit
                                }
                            }
                        }
                    }, onPress = {
                        awaitRelease()
                        hubItem.player.changeSettings(settings.copy(playbackSpeedFactor = 1f)).getOrThrow()
                    }, onLongPress = { (x, y) ->
                        if (state is PlayerState.Ready.Playing) {
                            val playbackSpeedFactor =
                                if (x in 0f..size.width / 2f && y in 0f..size.height.toFloat()) 0.5f else 2f
                            coroutineScope.launch {
                                hubItem.player.changeSettings(
                                    settings = settings.copy(playbackSpeedFactor = playbackSpeedFactor)
                                ).getOrThrow()
                            }
                        } else {
                            delete()
                        }
                    })
                }, contentAlignment = Alignment.Center
            ) {
                when (state) {
                    is PlayerState.Ready -> {
                        when ((state as PlayerState.Ready).media) {
                            is Media.Audio -> Icon(Icons.Default.AudioFile, null)

                            else -> hubItem.renderer?.let {
                                RendererComponent(
                                    modifier = Modifier.fillMaxSize(), foreground = Foreground(
                                        renderer = it, imageScale = ImageScale.Crop
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
                                    "$playbackTimestamp", modifier = Modifier.padding(8.dp), style = TextStyle(
                                        drawStyle = Stroke(
                                            miter = 2f, width = 1f, join = StrokeJoin.Round
                                        )
                                    )
                                )
                            }

                            if (settings.playbackSpeedFactor != 1f) {
                                Box(
                                    modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center
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
                    }

                    else -> Unit
                }
            }
        }
    }
}