package playlist.presentation

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.VolumeDown
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch

@Composable
fun VolumeControls(
    modifier: Modifier,
    volume: Float,
    isMuted: Boolean,
    toggleMute: suspend () -> Unit,
    changeVolume: suspend (Float) -> Unit,
    enabled: Boolean = true,
) {
    val coroutineScope = rememberCoroutineScope()

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(space = 8.dp, alignment = Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = {
                if (enabled) {
                    coroutineScope.launch {
                        toggleMute()
                    }
                }
            }, modifier = Modifier.alpha(if (enabled) 1f else .25f)
        ) {
            Icon(
                when {
                    isMuted -> Icons.Default.VolumeOff
                    else -> when {
                        volume == 0f -> Icons.Default.VolumeMute
                        volume < .25f -> Icons.Default.VolumeDown
                        else -> Icons.Default.VolumeUp
                    }
                }, contentDescription = "volume indicator", tint = MaterialTheme.colors.onSurface
            )
        }
        VolumeBar(
            modifier = Modifier.width(128.dp).height(16.dp).alpha(if (enabled) 1f else .25f),
            volume = volume,
            changeVolume = { value -> if (enabled) changeVolume(value) }
        )
    }
}