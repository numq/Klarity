package component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

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
    foregroundColor: Color = Color.LightGray,
) {
    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.CenterStart) {

        /**
         * Track
         */

        val trackSize = Size(constraints.maxWidth.toFloat(), constraints.maxHeight / 4f)

        val trackRange = remember(trackSize) { (0f..trackSize.width) }

        val animatedOffsetX = remember { Animatable(transform(value, valueRange, trackRange)) }

        /**
         * Thumb
         */

        val scaledThumbSize = constraints.maxHeight.toFloat()

        val thumbSize by remember(scaledThumbSize) {
            derivedStateOf {
                scaledThumbSize / 1.5f
            }
        }

        var draggingOffsetX by remember { mutableStateOf(0f) }

        var isThumbPressed by remember { mutableStateOf(false) }

        var isThumbDragging by remember { mutableStateOf(false) }

        val isThumbScaled by remember(isThumbPressed, isThumbDragging) {
            derivedStateOf {
                isThumbPressed || isThumbDragging
            }
        }

        val animatedThumbSize by animateFloatAsState(if (isThumbScaled) scaledThumbSize else thumbSize)

        LaunchedEffect(value, draggingOffsetX) {
            if (isThumbDragging) animatedOffsetX.snapTo(draggingOffsetX)
            else animatedOffsetX.animateTo(transform(value, valueRange, trackRange))
        }

        Canvas(modifier = Modifier.size(trackSize.width.dp, trackSize.height.dp)
            .padding(horizontal = scaledThumbSize.dp / 2).pointerInput(Unit) {
                detectTapGestures(onTap = { (x, _) ->
                    onValueChange(transform(x, trackRange, valueRange))
                })
            }) {
            drawLine(
                color = backgroundColor,
                start = Offset(trackRange.start, trackSize.height / 2),
                end = Offset(trackRange.endInclusive, trackSize.height / 2),
                strokeWidth = trackSize.height
            )
            drawLine(
                color = foregroundColor,
                start = Offset(trackRange.start, trackSize.height / 2),
                end = Offset(animatedOffsetX.value, trackSize.height / 2),
                strokeWidth = trackSize.height
            )
        }
        Spacer(modifier = Modifier.size(animatedThumbSize.dp)
            .offset(x = animatedOffsetX.value.dp - animatedThumbSize.dp / 2 + scaledThumbSize.dp / 2).alpha(.25f)
            .background(color = foregroundColor, shape = CircleShape).pointerInput(Unit) {
                detectTapGestures(onPress = {
                    isThumbPressed = true
                    tryAwaitRelease()
                    isThumbPressed = false
                })
            }.pointerInput(Unit) {
                detectDragGestures(onDragStart = {
                    draggingOffsetX = animatedOffsetX.value
                    isThumbDragging = true
                }, onDragCancel = {
                    isThumbDragging = false
                }, onDragEnd = {
                    onValueChange(transform(animatedOffsetX.value, trackRange, valueRange))
                    isThumbDragging = false
                }) { _, (x, _) ->
                    draggingOffsetX = (draggingOffsetX + x).coerceIn(trackRange)
                }
            })
    }
}