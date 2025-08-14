package io.github.numq.example.playlist.presentation

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.numq.example.playback.PlaybackState
import io.github.numq.example.playlist.PlaylistMode
import kotlin.time.Duration.Companion.seconds

@Composable
fun PlaylistControls(
    modifier: Modifier,
    playbackState: PlaybackState.Ready,
    color: Color,
    isShuffled: Boolean,
    shuffle: () -> Unit,
    mode: PlaylistMode,
    setMode: (PlaylistMode) -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
    previous: () -> Unit,
    next: () -> Unit,
    play: () -> Unit,
    pause: () -> Unit,
    resume: () -> Unit,
    stop: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = shuffle) {
            Icon(if (isShuffled) Icons.Default.ShuffleOn else Icons.Default.Shuffle, null, tint = color)
        }
        IconButton(
            onClick = {
                if (playbackState.playbackTimestamp < 2.seconds) {
                    previous()
                } else {
                    stop()
                    play()
                }
            },
            enabled = hasPrevious || playbackState.playbackTimestamp > 2.seconds,
            modifier = Modifier.alpha(if (hasPrevious || playbackState.playbackTimestamp > 2.seconds) 1f else .5f)
        ) {
            Icon(Icons.Default.SkipPrevious, null, tint = color)
        }
        IconButton(
            onClick = stop,
            enabled = playbackState !is PlaybackState.Ready.Stopped,
            modifier = Modifier.alpha(if (playbackState !is PlaybackState.Ready.Stopped) 1f else .5f)
        ) {
            Icon(Icons.Default.Stop, null, tint = color)
        }
        when (playbackState) {
            is PlaybackState.Ready.Playing -> IconButton(onClick = pause) {
                Icon(Icons.Default.Pause, null, tint = color)
            }

            is PlaybackState.Ready.Seeking -> {
                val infiniteTransition = rememberInfiniteTransition()

                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f, targetValue = 360f, animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2000, easing = LinearEasing)
                    )
                )

                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.rotate(angle), tint = color)
                }
            }

            else -> IconButton(onClick = {
                when (playbackState) {
                    is PlaybackState.Ready.Paused -> resume()

                    is PlaybackState.Ready.Stopped -> play()

                    is PlaybackState.Ready.Completed -> {
                        stop()

                        play()
                    }

                    else -> Unit
                }
            }) {
                Icon(Icons.Default.PlayArrow, null, tint = color)
            }
        }
        IconButton(
            onClick = next,
            enabled = hasNext,
            modifier = Modifier.alpha(if (hasNext) 1f else .5f)
        ) {
            Icon(Icons.Default.SkipNext, null, tint = color)
        }
        IconButton(onClick = {
            setMode(
                when (mode) {
                    PlaylistMode.NONE -> PlaylistMode.CIRCULAR

                    PlaylistMode.CIRCULAR -> PlaylistMode.SINGLE

                    PlaylistMode.SINGLE -> PlaylistMode.NONE
                }
            )
        }) {
            when (mode) {
                PlaylistMode.NONE -> Icon(Icons.Rounded.Repeat, null, tint = color)

                PlaylistMode.CIRCULAR -> Icon(Icons.Rounded.RepeatOn, null, tint = color)

                PlaylistMode.SINGLE -> Icon(Icons.Rounded.RepeatOneOn, null, tint = color)
            }
        }
    }
}