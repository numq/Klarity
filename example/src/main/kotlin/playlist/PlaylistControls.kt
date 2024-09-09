package playlist

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOn
import androidx.compose.material.icons.rounded.RepeatOneOn
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import queue.RepeatMode
import state.PlayerState
import timestamp.Timestamp

@Composable
fun PlaylistControls(
    modifier: Modifier,
    color: Color,
    state: PlayerState,
    playbackTimestamp: Timestamp,
    isShuffled: Boolean,
    shuffle: suspend () -> Unit,
    repeatMode: RepeatMode,
    setRepeatMode: suspend (RepeatMode) -> Unit,
    hasPrevious: Boolean,
    hasNext: Boolean,
    previous: suspend () -> Unit,
    next: suspend () -> Unit,
    play: suspend () -> Unit,
    pause: suspend () -> Unit,
    resume: suspend () -> Unit,
    stop: suspend () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()

    var interactionJob by remember { mutableStateOf<Job?>(null) }

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 16.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = {
            interactionJob?.cancel()
            interactionJob = coroutineScope.launch {
                shuffle()
            }
        }) {
            Icon(if (isShuffled) Icons.Default.ShuffleOn else Icons.Default.Shuffle, null, tint = color)
        }
        IconButton(onClick = {
            interactionJob?.cancel()
            interactionJob = coroutineScope.launch {
                if (playbackTimestamp.millis > 2000L) {
                    stop()
                    play()
                } else previous()
            }
        }, enabled = hasPrevious || playbackTimestamp.millis > 2000L) {
            Icon(Icons.Default.SkipPrevious, null, tint = color)
        }
        IconButton(onClick = {
            interactionJob?.cancel()
            interactionJob = coroutineScope.launch {
                stop()
            }
        }, enabled = state !is PlayerState.Ready.Stopped) {
            Icon(Icons.Default.Stop, null, tint = color)
        }
        when (state) {
            is PlayerState.Ready.Playing -> IconButton(onClick = {
                interactionJob?.cancel()
                interactionJob = coroutineScope.launch {
                    pause()
                }
            }) {
                Icon(Icons.Default.Pause, null, tint = color)
            }

            is PlayerState.Ready.Seeking -> {
                val infiniteTransition = rememberInfiniteTransition()

                val angle by infiniteTransition.animateFloat(
                    initialValue = 0f,
                    targetValue = 360f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(durationMillis = 2000, easing = LinearEasing)
                    )
                )

                IconButton(onClick = {}, enabled = false) {
                    Icon(Icons.Default.Sync, null, modifier = Modifier.rotate(angle), tint = color)
                }
            }

            else -> IconButton(onClick = {
                when (state) {
                    is PlayerState.Ready.Paused -> {
                        interactionJob?.cancel()
                        interactionJob = coroutineScope.launch {
                            resume()
                        }
                    }

                    is PlayerState.Ready.Stopped -> {
                        interactionJob?.cancel()
                        interactionJob = coroutineScope.launch {
                            play()
                        }
                    }

                    is PlayerState.Ready.Completed -> {
                        interactionJob?.cancel()
                        interactionJob = coroutineScope.launch {
                            stop()
                            play()
                        }
                    }

                    else -> Unit
                }
            }) {
                Icon(Icons.Default.PlayArrow, null, tint = color)
            }
        }
        IconButton(onClick = {
            interactionJob?.cancel()
            interactionJob = coroutineScope.launch {
                next()
            }
        }, enabled = hasNext) {
            Icon(Icons.Default.SkipNext, null, tint = color)
        }
        IconButton(onClick = {
            interactionJob?.cancel()
            interactionJob = coroutineScope.launch {
                setRepeatMode(
                    when (repeatMode) {
                        RepeatMode.NONE -> RepeatMode.CIRCULAR

                        RepeatMode.CIRCULAR -> RepeatMode.SINGLE

                        RepeatMode.SINGLE -> RepeatMode.NONE
                    }
                )
            }
        }) {
            when (repeatMode) {
                RepeatMode.NONE -> Icon(Icons.Rounded.Repeat, null, tint = color)

                RepeatMode.CIRCULAR -> Icon(Icons.Rounded.RepeatOn, null, tint = color)

                RepeatMode.SINGLE -> Icon(Icons.Rounded.RepeatOneOn, null, tint = color)
            }
        }
    }
}