package slider

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.PointerEventType
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun ValueSlider(
    modifier: Modifier,
    primaryValue: Float,
    secondaryValue: Float? = null,
    onPrimaryValueChange: suspend (Float) -> Unit,
    valueRange: ClosedRange<Float> = (0f..1f),
    backgroundColor: Color = Color.DarkGray,
    foregroundPrimaryColor: Color = Color.LightGray,
    foregroundSecondaryColor: Color = Color.Gray,
    thumbColor: Color = Color.White,
    interactionThumbColor: Color? = null,
    onHoveredValue: ((HoveredValue?) -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()

    var hoveringJob by remember { mutableStateOf<Job?>(null) }

    var draggingX by remember { mutableStateOf<Float?>(null) }

    BoxWithConstraints(modifier = modifier) {
        val thumbRadius = remember(constraints.maxHeight) {
            constraints.maxHeight.toFloat() / 2
        }

        val trackHeight = remember(constraints.maxHeight) {
            constraints.maxHeight.toFloat() / 4
        }

        val trackRange = remember(constraints.maxWidth.toFloat()) {
            (thumbRadius..constraints.maxWidth.toFloat() - thumbRadius)
        }

        Canvas(modifier = Modifier.fillMaxSize().pointerInput(maxWidth, maxHeight) {
            awaitEachGesture {
                val event = awaitPointerEvent()
                val position = event.changes.first().position

                when (event.type) {
                    PointerEventType.Move -> {
                        if (position.x in trackRange.start..trackRange.endInclusive && position.y in 0f..size.height.toFloat()) {
                            hoveringJob?.cancel()
                            hoveringJob = coroutineScope.launch {
                                val clampedX = position.x.coerceIn(trackRange.start, trackRange.endInclusive)
                                val newValue = sliderTransform(clampedX, trackRange, valueRange).coerceIn(
                                    valueRange.start, valueRange.endInclusive
                                )
                                onHoveredValue?.invoke(HoveredValue(offset = position, value = newValue))
                            }
                        } else {
                            onHoveredValue?.invoke(null)
                        }
                    }

                    PointerEventType.Exit -> onHoveredValue?.invoke(null)
                }

                event.changes.first().consume()
            }
        }.pointerInput(maxWidth, maxHeight) {
            detectTapGestures { (x, _) ->
                coroutineScope.launch {
                    val clampedX = x.coerceIn(trackRange.start, trackRange.endInclusive)
                    onPrimaryValueChange(
                        sliderTransform(
                            clampedX, trackRange, valueRange
                        ).coerceIn(valueRange.start, valueRange.endInclusive)
                    )
                }
            }
        }.pointerInput(maxWidth, maxHeight) {
            detectDragGestures(onDragStart = { (x, _) ->
                draggingX = x
            }, onDragCancel = {
                draggingX = null
                onHoveredValue?.invoke(null)
            }, onDragEnd = {
                draggingX?.let { x ->
                    val clampedX = x.coerceIn(trackRange.start, trackRange.endInclusive)
                    coroutineScope.launch {
                        onPrimaryValueChange(
                            sliderTransform(clampedX, trackRange, valueRange).coerceIn(
                                valueRange.start, valueRange.endInclusive
                            )
                        )
                        draggingX = null
                    }
                }
                onHoveredValue?.invoke(null)
            }) { change, (x, _) ->
                change.consume()
                if (change.position.x in trackRange.start..trackRange.endInclusive) {
                    if (draggingX != null) {
                        draggingX = (draggingX as Float) + x

                        hoveringJob?.cancel()
                        hoveringJob = coroutineScope.launch {
                            val clampedX = change.position.x.coerceIn(trackRange.start, trackRange.endInclusive)
                            val newValue = sliderTransform(clampedX, trackRange, valueRange).coerceIn(
                                valueRange.start, valueRange.endInclusive
                            )
                            onHoveredValue?.invoke(HoveredValue(offset = change.position, value = newValue))
                        }
                    } else {
                        onHoveredValue?.invoke(null)
                    }
                } else {
                    onHoveredValue?.invoke(null)
                }
            }
        }) {
            drawLine(
                color = backgroundColor,
                start = Offset(trackRange.start, thumbRadius),
                end = Offset(trackRange.endInclusive, thumbRadius),
                strokeWidth = trackHeight,
                cap = StrokeCap.Square
            )
            secondaryValue?.let {
                drawLine(
                    color = foregroundSecondaryColor,
                    start = Offset(trackRange.start, thumbRadius),
                    end = Offset(draggingX ?: sliderTransform(it, valueRange, trackRange), thumbRadius),
                    strokeWidth = trackHeight,
                    cap = StrokeCap.Square
                )
            }
            drawLine(
                color = foregroundPrimaryColor,
                start = Offset(trackRange.start, thumbRadius),
                end = Offset(draggingX ?: sliderTransform(primaryValue, valueRange, trackRange), thumbRadius),
                strokeWidth = trackHeight,
                cap = StrokeCap.Square
            )
            drawCircle(
                color = (if (draggingX != null) interactionThumbColor else thumbColor) ?: thumbColor,
                radius = thumbRadius,
                center = Offset(draggingX ?: sliderTransform(primaryValue, valueRange, trackRange), thumbRadius)
            )
        }
    }
}