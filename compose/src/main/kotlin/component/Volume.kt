package component

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun Volume(
    volume: Float,
    isMuted: Boolean,
    toggleMute: () -> Unit,
    changeVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
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
        modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = toggleMute) {
            Icon(
                when {
                    isMuted -> Icons.Rounded.VolumeOff
                    else -> when {
                        volume == 0f -> Icons.Rounded.VolumeMute
                        volume < .5f -> Icons.Rounded.VolumeDown
                        else -> Icons.Rounded.VolumeUp
                    }
                }, contentDescription = "volume indicator"
            )
        }

        Slider(
            volume,
            onValueChange = changeVolume,
            modifier = Modifier.weight(1f),
            colors = volumeBarColors,
            interactionSource = MutableInteractionSource()
        )
    }
}