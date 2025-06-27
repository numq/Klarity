package playlist.presentation

import androidx.compose.material.MaterialTheme
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
        modifier = modifier,
        foregroundPrimaryColor = MaterialTheme.colors.primary,
        foregroundSecondaryColor = MaterialTheme.colors.primaryVariant,
        thumbColor = MaterialTheme.colors.primary,
    )
}