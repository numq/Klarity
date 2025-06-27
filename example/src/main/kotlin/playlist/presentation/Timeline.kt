package playlist.presentation

import androidx.compose.material.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import slider.ValueSlider
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

@Composable
fun Timeline(
    modifier: Modifier,
    duration: Duration,
    bufferTimestamp: Duration,
    playbackTimestamp: Duration,
    seekTo: suspend (Duration) -> Unit,
    onPreviewTimestamp: ((PreviewTimestamp?) -> Unit)? = null,
) {
    ValueSlider(
        modifier = modifier,
        primaryValue = playbackTimestamp.inWholeMilliseconds.toFloat(),
        secondaryValue = bufferTimestamp.inWholeMilliseconds.toFloat(),
        onPrimaryValueChange = { value -> seekTo(value.toLong().milliseconds) },
        valueRange = (0f..duration.inWholeMilliseconds.toFloat()),
        foregroundPrimaryColor = MaterialTheme.colors.primary,
        foregroundSecondaryColor = MaterialTheme.colors.primaryVariant,
        thumbColor = MaterialTheme.colors.primary,
        onHoveredValue = { hoveredValue ->
            onPreviewTimestamp?.invoke(
                hoveredValue?.run {
                    PreviewTimestamp(offset = offset, timestamp = value.toLong().milliseconds)
                }
            )
        }
    )
}