package playlist.presentation

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import slider.ValueSlider

@Composable
fun VolumeBar(
    modifier: Modifier,
    volume: Float,
    changeVolume: suspend (Float) -> Unit,
) {
    ValueSlider(
        primaryValue = volume,
        onPrimaryValueChange = changeVolume,
        modifier = modifier
    )
}