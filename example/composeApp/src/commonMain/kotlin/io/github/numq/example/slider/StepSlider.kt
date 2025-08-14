package io.github.numq.example.slider

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DragIndicator
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable
fun StepSlider(steps: Int, step: Int, sliderColor: Color, pointColor: Color, onValueChange: (Int) -> Unit) {
    require(steps > 0) { "Steps should be positive" }

    require(step in 0..steps) { "Step is out of range" }

    val coroutineScope = rememberCoroutineScope()

    val horizontalPadding = 8.dp

    var iconWidth by remember { mutableStateOf(0f) }

    var sliderWidth by remember { mutableStateOf(0f) }

    val stepWidth by remember(sliderWidth) {
        derivedStateOf {
            if (steps > 1) {
                (sliderWidth - 2 * horizontalPadding.value) / (steps - 1)
            } else {
                0f
            }
        }
    }

    val offsetX = remember { Animatable(0f) }
    val x by offsetX.asState()

    LaunchedEffect(stepWidth) {
        if (stepWidth > 0) {
            offsetX.snapTo(step * stepWidth)
        }
    }

    LaunchedEffect(x) {
        if (stepWidth > 0 && x % stepWidth == 0f) {
            onValueChange((x / stepWidth).roundToInt())
        }
    }

    Box(
        modifier = Modifier.fillMaxWidth().padding(horizontal = horizontalPadding, vertical = 8.dp)
            .pointerInput(stepWidth) {
                detectTapGestures { tapOffset ->
                    coroutineScope.launch {
                        val snappedValue = calculateSnappedValue(tapOffset.x - horizontalPadding.value, stepWidth)

                        onValueChange(snappedValue)

                        offsetX.animateTo(snappedValue * stepWidth)
                    }
                }
            }.pointerInput(iconWidth, sliderWidth, stepWidth) {
                detectDragGestures(
                    onDragCancel = {
                        coroutineScope.launch {
                            snapToNearestStep(offsetX, stepWidth)

                            onValueChange((offsetX.value / stepWidth).roundToInt())
                        }
                    },
                    onDragEnd = {
                        coroutineScope.launch {
                            snapToNearestStep(offsetX, stepWidth)

                            onValueChange((offsetX.value / stepWidth).roundToInt())
                        }
                    }
                ) { change, _ ->
                    change.consume()
                    coroutineScope.launch {
                        val newX = (change.position.x - horizontalPadding.value).coerceIn(
                            0f,
                            sliderWidth - 2 * horizontalPadding.value
                        )

                        offsetX.snapTo(newX)

                        onValueChange((newX / stepWidth).roundToInt())
                    }
                }
            }.drawBehind {
                repeat(steps) { step ->
                    drawCircle(
                        color = pointColor,
                        center = Offset(step * stepWidth + horizontalPadding.value, size.height / 2),
                        radius = size.height / 2,
                    )
                }
            }.onGloballyPositioned {
                sliderWidth = it.size.width.toFloat()
            },
        contentAlignment = Alignment.CenterStart
    ) {
        Icon(
            imageVector = Icons.Default.DragIndicator,
            contentDescription = null,
            modifier = Modifier.onGloballyPositioned {
                iconWidth = it.size.width.toFloat()
            }.graphicsLayer {
                translationX = x + horizontalPadding.value - iconWidth / 2
            },
            tint = sliderColor
        )
    }
}

private suspend fun snapToNearestStep(offsetX: Animatable<Float, *>, stepWidth: Float) {
    if (stepWidth > 0) {
        val snappedValue = calculateSnappedValue(offsetX.value, stepWidth)
        offsetX.animateTo(snappedValue * stepWidth)
    }
}

private fun calculateSnappedValue(currentX: Float, stepWidth: Float) = (currentX / stepWidth).roundToInt()