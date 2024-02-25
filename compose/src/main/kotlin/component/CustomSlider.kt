package component

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.launch

private fun transform(value: Float, source: ClosedRange<Float>, target: ClosedRange<Float>): Float {
    if (source.endInclusive - source.start == 0f) return target.start
    return ((value - source.start) / (source.endInclusive - source.start)) * (target.endInclusive - target.start) + target.start
}

@Composable
fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedRange<Float> = (0f..1f),
    modifier: Modifier = Modifier,
    backgroundColor: Color = Color.DarkGray,
    foregroundColor: Color = Color.Gray,
    thumbColor: Color = Color.White,
    interactionThumbColor: Color? = null,
) {

    val coroutineScope = rememberCoroutineScope()

    BoxWithConstraints(modifier = modifier) {

        /**
         * Track
         */

        val trackSize = remember(constraints.maxWidth, constraints.maxHeight) {
            Size(constraints.maxWidth.toFloat(), constraints.maxHeight.toFloat())
        }

        val trackRange = remember(trackSize.width) { (0f..trackSize.width) }

        val animatedOffsetX = remember { Animatable(transform(value, valueRange, trackRange)) }

        /**
         * Thumb
         */

        val thumbRadius = remember(trackSize.height) { trackSize.height / 2 }

        var isThumbPressed by remember { mutableStateOf(false) }

        var isThumbDragging by remember { mutableStateOf(false) }

        LaunchedEffect(value) {
            if (!(isThumbPressed || isThumbDragging)) animatedOffsetX.animateTo(
                transform(
                    value,
                    valueRange,
                    trackRange
                )
            )
        }

        Canvas(modifier = Modifier.fillMaxSize().pointerInput(trackRange, valueRange) {
            detectTapGestures(onTap = { (x, _) ->
                onValueChange(transform(x, trackRange, valueRange))
                isThumbPressed = false
            }, onPress = {
                isThumbPressed = true
            })
        }.pointerInput(trackRange, valueRange) {
            detectDragGestures(onDragStart = { (x, _) ->
                coroutineScope.launch {
                    animatedOffsetX.snapTo(x)
                }
                isThumbDragging = true
            }, onDragCancel = {
                isThumbDragging = false
            }, onDragEnd = {
                onValueChange(transform(animatedOffsetX.value, trackRange, valueRange))

                isThumbDragging = false
            }) { change, (x, _) ->
                if (change.position.x in trackRange) {
                    coroutineScope.launch {
                        animatedOffsetX.snapTo((animatedOffsetX.value + x).coerceIn(trackRange))
                    }
                }
            }
        }) {
            drawLine(
                color = backgroundColor,
                start = Offset(trackRange.start + thumbRadius, trackSize.height / 2),
                end = Offset(trackRange.endInclusive - thumbRadius, trackSize.height / 2),
                strokeWidth = trackSize.height,
                cap = StrokeCap.Round
            )
            drawLine(
                color = foregroundColor,
                start = Offset(trackRange.start + thumbRadius, trackSize.height / 2),
                end = Offset(
                    transform(
                        animatedOffsetX.value,
                        trackRange,
                        (trackRange.start + thumbRadius..trackRange.endInclusive - thumbRadius)
                    ), trackSize.height / 2
                ),
                strokeWidth = trackSize.height,
                cap = StrokeCap.Round
            )
            drawCircle(
                color = interactionThumbColor.takeIf { isThumbPressed || isThumbDragging } ?: thumbColor,
                radius = thumbRadius,
                center = Offset(
                    transform(
                        animatedOffsetX.value,
                        trackRange,
                        (trackRange.start + thumbRadius..trackRange.endInclusive - thumbRadius)
                    ), trackSize.height / 2
                )
            )
        }
    }
}