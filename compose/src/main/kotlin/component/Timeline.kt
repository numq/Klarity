package component

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.Slider
import androidx.compose.material.SliderColors
import androidx.compose.material.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun Timeline(
    timestampMillis: Long,
    durationMillis: Long,
    seekTo: (Long) -> Unit,
    interactionSource: MutableInteractionSource = MutableInteractionSource(),
    seekBarColors: SliderColors = SliderDefaults.colors(
        thumbColor = Color.Gray,
        disabledThumbColor = Color.LightGray,
        activeTickColor = Color.DarkGray,
        activeTrackColor = Color.DarkGray,
        inactiveTickColor = Color.LightGray,
        inactiveTrackColor = Color.LightGray
    ),
) {
    val animatedTimestamp by animateFloatAsState(timestampMillis.toFloat())
    Slider(
        animatedTimestamp,
        valueRange = (0f..durationMillis.toFloat()),
        onValueChange = { seekTo(it.toLong()) },
        interactionSource = interactionSource,
        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp),
        colors = seekBarColors
    )
}