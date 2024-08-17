package component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import component.slider.CustomSlider

@Composable
fun VolumeBar(
    volume: Float,
    changeVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.DarkGray,
    foregroundColor: Color = Color.LightGray,
) {
    CustomSlider(
        primaryValue = volume,
        onPrimaryValueChange = changeVolume,
        modifier = modifier,
        backgroundColor = backgroundColor,
        foregroundPrimaryColor = foregroundColor
    )
}