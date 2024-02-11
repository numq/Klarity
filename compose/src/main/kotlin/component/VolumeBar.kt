package component

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color

@Composable
fun VolumeBar(
    volume: Float,
    changeVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.DarkGray,
    foregroundColor: Color = Color.LightGray,
) {
    CustomSlider(
        value = volume,
        onValueChange = changeVolume,
        modifier = modifier,
        backgroundColor = backgroundColor,
        foregroundColor = foregroundColor
    )
}