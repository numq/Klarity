package controls

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import slider.ValueSlider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun Timeline(
    modifier: Modifier,
    bufferTimestamp: Duration,
    playbackTimestamp: Duration,
    durationTimestamp: Duration,
    seekTo: suspend (Duration) -> Unit,
    onHoveredTimestamp: ((HoveredTimestamp?) -> Unit)? = null,
) {
    ValueSlider(
        modifier = modifier,
        primaryValue = playbackTimestamp.inWholeMilliseconds.toFloat(),
        secondaryValue = bufferTimestamp.inWholeMilliseconds.toFloat(),
        onPrimaryValueChange = { value -> seekTo(value.toLong().milliseconds) },
        valueRange = (0f..durationTimestamp.inWholeMilliseconds.toFloat()),
        foregroundPrimaryColor = MaterialTheme.colors.primary,
        foregroundSecondaryColor = MaterialTheme.colors.primaryVariant,
        thumbColor = MaterialTheme.colors.primary,
        onHoveredValue = { hoveredValue ->
            onHoveredTimestamp?.invoke(
                hoveredValue?.run {
                    HoveredTimestamp(offset = offset, timestamp = value.toLong().milliseconds)
                }
            )
        }
    )
}