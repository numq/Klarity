package component

import androidx.compose.material.Slider
import androidx.compose.material.SliderColors
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import theme.ZeroRippleTheme

@Composable
fun VolumeBar(
    volume: Float,
    changeVolume: (Float) -> Unit,
    modifier: Modifier = Modifier,
    volumeBarColors: SliderColors,
) {

    val (changingValue, setChangingValue) = remember {
        mutableStateOf<Float?>(null)
    }

    ZeroRippleTheme {
        Slider(
            value = changingValue ?: volume,
            onValueChange = setChangingValue,
            onValueChangeFinished = {
                changingValue?.let(changeVolume)
                setChangingValue(null)
            },
            modifier = modifier,
            colors = volumeBarColors
        )
    }
}