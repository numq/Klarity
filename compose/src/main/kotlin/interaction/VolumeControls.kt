package interaction

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
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
import androidx.compose.ui.unit.dp
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
    backgroundColor: Color = Color.DarkGray,
    foregroundColor: Color = Color.LightGray,
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
            modifier = Modifier.fillMaxWidth().height(8.dp).alpha(if (enabled) 1f else .25f),
            backgroundColor = backgroundColor,
            foregroundColor = foregroundColor
        )
    }
}