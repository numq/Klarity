package controls

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import slider.ValueSlider

@Composable
fun Timeline(
    modifier: Modifier,
    bufferTimestampMillis: Long,
    playbackTimestampMillis: Long,
    durationTimestampMillis: Long,
    seekTo: suspend (Long) -> Unit,
    onHoveredTimestamp: ((HoveredTimestamp?) -> Unit)? = null,
) {
    ValueSlider(
        modifier = modifier,
        primaryValue = playbackTimestampMillis.toFloat(),
        secondaryValue = bufferTimestampMillis.toFloat(),
        onPrimaryValueChange = { value -> seekTo(value.toLong()) },
        valueRange = (0f..durationTimestampMillis.toFloat()),
        foregroundPrimaryColor = MaterialTheme.colors.primary,
        foregroundSecondaryColor = MaterialTheme.colors.primaryVariant,
        thumbColor = MaterialTheme.colors.primary,
        onHoveredValue = { hoveredValue ->
            onHoveredTimestamp?.invoke(
                hoveredValue?.run {
                    HoveredTimestamp(offset = offset, millis = value.toLong())
                }
            )
        }
    )
}