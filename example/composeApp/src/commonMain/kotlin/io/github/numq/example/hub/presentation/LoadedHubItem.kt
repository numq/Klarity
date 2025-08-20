package io.github.numq.example.hub.presentation

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.HoverInteraction
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material3.*
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
import io.github.numq.example.item.Item
import io.github.numq.example.playback.PlaybackState
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.renderer.compose.Foreground
import io.github.numq.klarity.renderer.compose.ImageScale
import io.github.numq.klarity.renderer.compose.RendererComponent

@Composable
fun LoadedHubItem(
    item: Item.Loaded,
    playbackItem: Item.Loaded?,
    playbackState: PlaybackState,
    renderer: Renderer?,
    startPreview: () -> Unit,
    stopPreview: () -> Unit,
    startPlayback: () -> Unit,
    stopPlayback: () -> Unit,
    decreasePlaybackSpeed: () -> Unit,
    increasePlaybackSpeed: () -> Unit,
    resetPlaybackSpeed: () -> Unit,
    remove: () -> Unit,
) {
    val isPlaying by remember(playbackState, playbackItem, item) {
        derivedStateOf {
            playbackState is PlaybackState.Ready.Playing && playbackItem?.id == item.id
        }
    }

    val interactionSource = remember { MutableInteractionSource() }

    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is HoverInteraction.Enter -> {
                    if (!isPlaying) {
                        startPreview()
                    }
                }

                is HoverInteraction.Exit -> {
                    if (!isPlaying) {
                        stopPreview()
                    }
                }
            }
        }
    }

    Box(modifier = Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
        Card {
            Box(
                modifier = Modifier.fillMaxSize().aspectRatio(1f).hoverable(interactionSource = interactionSource)
                    .pointerInput(isPlaying) {
                        detectTapGestures(onTap = {
                            if (isPlaying) stopPlayback() else startPlayback()
                        }, onPress = {
                            awaitRelease()

                            resetPlaybackSpeed()
                        }, onLongPress = { (x, y) ->
                            if (!isPlaying) {
                                remove()
                            } else if (x in 0f..size.width / 2f && y in 0f..size.height.toFloat()) {
                                decreasePlaybackSpeed()
                            } else {
                                increasePlaybackSpeed()
                            }
                        })
                    }, contentAlignment = Alignment.Center
            ) {
                when {
                    item.width == 0 || item.height == 0 -> Icon(
                        Icons.Default.AudioFile, null, tint = MaterialTheme.colorScheme.primary
                    )

                    renderer != null -> RendererComponent(
                        modifier = Modifier.fillMaxSize(),
                        foreground = Foreground(renderer = renderer, imageScale = ImageScale.Crop),
                        placeholder = {
                            CircularProgressIndicator()
                        })

                    else -> Icon(
                        Icons.Default.BrokenImage, null, tint = MaterialTheme.colorScheme.primary
                    )
                }

                if (isPlaying) {
                    Box(
                        modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.BottomStart
                    ) {
                        Text(
                            "${(playbackState as PlaybackState.Ready).playbackTimestamp}",
                            modifier = Modifier.padding(8.dp),
                            style = TextStyle(
                                drawStyle = Stroke(
                                    miter = 2f, width = 1f, join = StrokeJoin.Round
                                )
                            ),
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    if ((playbackState as PlaybackState.Ready).playbackSpeedFactor != 1f) {
                        Box(
                            modifier = Modifier.fillMaxSize().padding(8.dp), contentAlignment = Alignment.Center
                        ) {
                            Text(
                                "Playing on ${
                                    playbackState.playbackSpeedFactor.toString().replace(".0", "")
                                }x speed", modifier = Modifier.drawBehind {
                                    drawRoundRect(
                                        color = Color.Black.copy(alpha = .5f), cornerRadius = CornerRadius(16f, 16f)
                                    )
                                }.padding(8.dp), color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        }
    }
}