package interaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.SliderColors
import androidx.compose.material.SliderDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.VolumeDown
import androidx.compose.material.icons.rounded.VolumeMute
import androidx.compose.material.icons.rounded.VolumeOff
import androidx.compose.material.icons.rounded.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import component.VolumeBar

@Composable
fun VolumeControls(
    enabled: Boolean,
    volume: Float,
    isMuted: Boolean,
    toggleMute: () -> Unit,
    changeVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
    iconTint: Color = Color.White,
    volumeBarColors: SliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        disabledThumbColor = Color.LightGray,
        activeTickColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTickColor = Color.LightGray,
        inactiveTrackColor = Color.LightGray
    ),
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = { if (enabled) toggleMute() }, modifier = Modifier.alpha(if (enabled) 1f else .25f)
        ) {
            Icon(
                when {
                    isMuted -> Icons.Rounded.VolumeOff
                    else -> when {
                        volume == 0f -> Icons.Rounded.VolumeMute
                        volume < .25f -> Icons.Rounded.VolumeDown
                        else -> Icons.Rounded.VolumeUp
                    }
                }, contentDescription = "volume indicator", tint = iconTint
            )
        }
        VolumeBar(
            volume = volume,
            changeVolume = { value -> if (enabled) changeVolume(value) },
            modifier = Modifier.weight(1f).alpha(if (enabled) 1f else .25f),
            volumeBarColors = volumeBarColors,
        )
    }
}