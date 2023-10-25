package component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import extension.formatTimestamp
import player.PlaybackStatus
import player.PlayerState
import kotlin.math.roundToInt

@Composable
fun Controls(
    timestampMillis: Long,
    durationMillis: Long,
    state: PlayerState,
    status: PlaybackStatus,
    play: () -> Unit,
    pause: () -> Unit,
    stop: () -> Unit,
    toggleMute: () -> Unit,
    changeVolume: (Double) -> Unit,
    playInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    pauseInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    stopInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    muteInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    volumeInteractionSource: MutableInteractionSource = remember { MutableInteractionSource() },
    volumeBarColors: SliderColors = SliderDefaults.colors(
        thumbColor = Color.Gray,
        disabledThumbColor = Color.LightGray,
        activeTickColor = Color.DarkGray,
        activeTrackColor = Color.DarkGray,
        inactiveTickColor = Color.LightGray,
        inactiveTrackColor = Color.LightGray
    ),
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            when (status) {
                PlaybackStatus.BUFFERING, PlaybackStatus.PLAYING, PlaybackStatus.SEEKING -> IconButton(
                    pause, interactionSource = pauseInteractionSource
                ) {
                    Icon(Icons.Rounded.Pause, "pause")
                }

                PlaybackStatus.PAUSED, PlaybackStatus.STOPPED, PlaybackStatus.COMPLETED -> IconButton(
                    play, interactionSource = playInteractionSource
                ) {
                    Icon(Icons.Rounded.PlayArrow, "play")
                }

                PlaybackStatus.EMPTY -> Icon(Icons.Rounded.Error, "empty media")
            }
            IconButton(stop, interactionSource = stopInteractionSource) {
                Icon(Icons.Rounded.Stop, "stop")
            }
            Text("${timestampMillis.formatTimestamp()}/${durationMillis.formatTimestamp()}")
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                IconButton(onClick = toggleMute, interactionSource = muteInteractionSource) {
                    Icon(
                        when {
                            state.isMuted -> Icons.Rounded.VolumeOff
                            else -> when {
                                state.volume == 0.0 -> Icons.Rounded.VolumeMute
                                state.volume < .5 -> Icons.Rounded.VolumeDown
                                else -> Icons.Rounded.VolumeUp
                            }
                        }, contentDescription = "volume"
                    )
                }
            }
            Slider(
                if (state.volume > 0) (state.volume * 100).toFloat() else 0f,
                valueRange = (0f..100f),
                onValueChange = { changeVolume(it.roundToInt() / 100.0) },
                interactionSource = volumeInteractionSource,
                modifier = Modifier.fillMaxWidth(.25f),
                colors = volumeBarColors
            )
        }
    }
}