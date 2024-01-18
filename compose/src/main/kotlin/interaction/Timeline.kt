package interaction

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material.Slider
import androidx.compose.material.SliderColors
import androidx.compose.material.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import theme.ZeroRippleTheme

@Composable
fun Timeline(
    playbackTimestampMillis: Long,
    durationTimestampMillis: Long,
    seekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
    timelineColors: SliderColors = SliderDefaults.colors(
        thumbColor = Color.White,
        disabledThumbColor = Color.LightGray,
        activeTickColor = Color.White,
        activeTrackColor = Color.White,
        inactiveTickColor = Color.LightGray,
        inactiveTrackColor = Color.LightGray
    ),
) {
    val animatedTimelineValue by animateFloatAsState(playbackTimestampMillis.toFloat())

    ZeroRippleTheme {
        Slider(
            value = animatedTimelineValue,
            onValueChange = { value ->
                seekTo(value.toLong())
            },
            valueRange = (0f..durationTimestampMillis.toFloat()),
            modifier = modifier,
            colors = timelineColors
        )
    }
}