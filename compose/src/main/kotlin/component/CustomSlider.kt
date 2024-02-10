package component

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import kotlinx.coroutines.launch

@Composable
fun CustomSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedRange<Float> = (0f..1f),
    modifier: Modifier = Modifier,
    thumbSize: Float = 16f,
    backgroundColor: Color = Color.DarkGray,
    foregroundColor: Color = Color.LightGray,
) {
    fun offsetToValue(offsetX: Float, size: Size, bounds: ClosedRange<Float>): Float {
        if (bounds.isEmpty()) return 0f
        return ((offsetX / size.width) * (bounds.endInclusive - bounds.start) + bounds.start)
    }

    fun valueToOffset(value: Float, min: Float, max: Float, start: Float, end: Float): Float {
        if (start < 0f || end < 0f) return 0f
        return ((value - min) / (max - min) * end)
    }

    var isDragging by remember { mutableStateOf(false) }

    var offsetX by remember { mutableStateOf(0f) }

    var trackSize by remember { mutableStateOf(Size.Zero) }

    val start = 0f

    val end = trackSize.width

    val animationScope = rememberCoroutineScope()

    val animatedTimelineValue = remember { Animatable(value) }

    LaunchedEffect(value) {
        if (!isDragging) animatedTimelineValue.animateTo(value, tween(durationMillis = 50, easing = LinearEasing))
    }

    val draggingThumbSize = remember(thumbSize) { thumbSize * 1.5f }

    val animatedThumbSize by animateFloatAsState(if (isDragging) draggingThumbSize else thumbSize)

    Box(modifier = modifier.padding(horizontal = draggingThumbSize.dp / 2).onGloballyPositioned { coordinates ->
        trackSize = coordinates.size.toSize()
    }.pointerInput(Unit) {
        detectTapGestures { (x, _) ->
            onValueChange(offsetToValue(x, trackSize, valueRange))
        }
    }.drawBehind {
        drawLine(
            color = backgroundColor,
            start = Offset(start, trackSize.height / 2),
            end = Offset(end, trackSize.height / 2),
            strokeWidth = trackSize.height
        )

        drawLine(
            color = foregroundColor, start = Offset(start, trackSize.height / 2), end = Offset(
                if (isDragging) offsetX else valueToOffset(
                    animatedTimelineValue.value,
                    valueRange.start,
                    valueRange.endInclusive,
                    start,
                    end
                ), trackSize.height / 2
            ), strokeWidth = trackSize.height
        )
    }.pointerInput(Unit) {
        detectDragGestures(onDragStart = { (x, _) ->
            offsetX = x
            isDragging = true
        }, onDragCancel = {
            isDragging = false
        }, onDragEnd = {
            onValueChange(offsetToValue(offsetX, trackSize, valueRange))
            animationScope.launch {
                animatedTimelineValue.snapTo(offsetToValue(offsetX, trackSize, valueRange))
                isDragging = false
            }
        }) { change, (dragX, _) ->
            change.position.run {
                if (x in 0f..size.width.toFloat()) {
                    offsetX += dragX
                    animationScope.launch {
                        animatedTimelineValue.snapTo(offsetToValue(offsetX, trackSize, valueRange))
                    }
                }
            }
        }
    }.drawWithContent {
        drawCircle(
            foregroundColor,
            radius = animatedThumbSize / 2,
            center = Offset(
                if (isDragging) offsetX else valueToOffset(
                    animatedTimelineValue.value,
                    valueRange.start,
                    valueRange.endInclusive,
                    start,
                    end
                ), size.height / 2
            )
        )
    })
}