package interaction

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import component.CustomSlider

@Composable
fun Timeline(
    playbackTimestampMillis: Long,
    durationTimestampMillis: Long,
    seekTo: (Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    CustomSlider(
        value = playbackTimestampMillis.toFloat(),
        onValueChange = { value -> seekTo(value.toLong()) },
        valueRange = (0f..durationTimestampMillis.toFloat()),
        modifier = modifier
    )
}