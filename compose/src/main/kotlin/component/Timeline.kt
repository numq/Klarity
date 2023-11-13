package component

import androidx.compose.material.Slider
import androidx.compose.material.SliderColors
import androidx.compose.material.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import kotlin.math.roundToLong

@Composable
fun Timeline(
    timestampMillis: Long,
    durationMillis: Long,
    seekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    seekBarColors: SliderColors = SliderDefaults.colors(
        thumbColor = Color.Gray,
        disabledThumbColor = Color.LightGray,
        activeTickColor = Color.DarkGray,
        activeTrackColor = Color.DarkGray,
        inactiveTickColor = Color.LightGray,
        inactiveTrackColor = Color.LightGray
    ),
) {
    Slider(
        timestampMillis.toFloat(),
        valueRange = (0f..durationMillis.toFloat()),
        onValueChange = { value -> seekTo(value.roundToLong().coerceIn(0L, durationMillis)) },
        modifier = modifier,
        colors = seekBarColors
    )
}