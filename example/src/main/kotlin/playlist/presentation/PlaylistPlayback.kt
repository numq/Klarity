package playlist.presentation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.renderer.compose.Background
import io.github.numq.klarity.renderer.compose.Foreground
import io.github.numq.klarity.renderer.compose.RendererComponent
import playback.PlaybackState
import playlist.PlaylistMode
import timestamp.formatTimestamp
import kotlin.time.Duration

@Composable
fun ColumnScope.PlaylistPlayback(
    playbackState: PlaybackState.Ready,
    playbackRenderer: Renderer?,
    previewRenderer: Renderer?,
    previewTimestamp: PreviewTimestamp?,
    isOverlayVisible: Boolean,
    showOverlay: () -> Unit,
    hideOverlay: () -> Unit,
    isShuffled: Boolean,
    mode: PlaylistMode,
    hasPrevious: Boolean,
    hasNext: Boolean,
    shuffle: () -> Unit,
    setMode: (PlaylistMode) -> Unit,
    previous: () -> Unit,
    next: () -> Unit,
    play: () -> Unit,
    pause: () -> Unit,
    resume: () -> Unit,
    stop: () -> Unit,
    seekTo: (timestamp: Duration) -> Unit,
    toggleMute: () -> Unit,
    changeVolume: (Float) -> Unit,
    decreasePlaybackSpeed: () -> Unit,
    increasePlaybackSpeed: () -> Unit,
    resetPlaybackSpeed: () -> Unit,
    onPreviewTimestamp: (PreviewTimestamp?) -> Unit,
) {
    Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.BottomStart) {
                Box(
                    modifier = Modifier.fillMaxSize().pointerInput(isOverlayVisible) {
                        detectTapGestures(onPress = {
                            when {
                                isOverlayVisible -> hideOverlay()

                                else -> showOverlay()
                            }

                            awaitRelease()

                            resetPlaybackSpeed()
                        }, onLongPress = { (x, y) ->
                            if (x in 0f..size.width / 2f && y in 0f..size.height.toFloat()) {
                                decreasePlaybackSpeed()
                            } else {
                                increasePlaybackSpeed()
                            }
                        })
                    }, contentAlignment = Alignment.Center
                ) {
                    when {
                        playbackRenderer == null -> Icon(Icons.Default.AudioFile, null)

                        playbackRenderer.drawsNothing() -> Icon(Icons.Default.BrokenImage, null)

                        else -> RendererComponent(
                            modifier = Modifier.fillMaxSize(),
                            foreground = Foreground(renderer = playbackRenderer),
                            background = Background.Blur()
                        )
                    }

                    if (playbackState.playbackSpeedFactor != 1f) {
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
                                }.padding(8.dp), color = Color.White
                            )
                        }
                    }
                }
                this@Column.AnimatedVisibility(
                    visible = isOverlayVisible,
                    enter = slideInVertically { it },
                    exit = slideOutVertically { it }) {
                    Column(
                        modifier = Modifier.fillMaxWidth().background(MaterialTheme.colors.surface.copy(alpha = .5f))
                            .padding(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.CenterVertically)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            BoxWithConstraints(
                                modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart
                            ) {
                                Text(
                                    text = "${playbackState.playbackTimestamp.inWholeMilliseconds.formatTimestamp()}/${playbackState.duration.inWholeMilliseconds.formatTimestamp()}",
                                    modifier = Modifier.padding(8.dp),
                                    color = MaterialTheme.colors.onSurface
                                )
                            }
                            PlaylistControls(
                                modifier = Modifier.wrapContentWidth(),
                                playbackState = playbackState,
                                color = MaterialTheme.colors.onSurface,
                                isShuffled = isShuffled,
                                shuffle = shuffle,
                                mode = mode,
                                setMode = setMode,
                                hasPrevious = hasPrevious,
                                hasNext = hasNext,
                                previous = previous,
                                next = next,
                                play = play,
                                pause = pause,
                                resume = resume,
                                stop = stop
                            )
                            Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                                VolumeControls(
                                    modifier = Modifier.fillMaxWidth(),
                                    volume = playbackState.volume,
                                    isMuted = playbackState.isMuted,
                                    toggleMute = toggleMute,
                                    changeVolume = changeVolume
                                )
                            }
                        }
                        Timeline(
                            modifier = Modifier.fillMaxWidth().height(24.dp).padding(4.dp),
                            duration = playbackState.duration,
                            bufferTimestamp = playbackState.bufferTimestamp,
                            playbackTimestamp = playbackState.playbackTimestamp,
                            seekTo = seekTo,
                            onPreviewTimestamp = onPreviewTimestamp
                        )
                    }
                    if (previewTimestamp != null) {
                        previewRenderer?.takeIf { !it.drawsNothing() }?.let {
                            TimelinePreview(
                                width = 128f,
                                height = 128f,
                                bottomPadding = 24f,
                                previewTimestamp = previewTimestamp,
                                renderer = previewRenderer,
                            )
                        }
                    }
                }
            }
        }
    }
}