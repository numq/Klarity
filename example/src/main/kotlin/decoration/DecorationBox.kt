package decoration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import java.awt.Point
import kotlin.math.roundToInt

@Composable
fun ComposeWindow.DecorationBox(exitApplication: () -> Unit, content: @Composable RowScope.() -> Unit) {

    var decorationOffsetX by rememberSaveable { mutableStateOf(0f) }

    var decorationOffsetY by rememberSaveable { mutableStateOf(0f) }

    val decorationOffset by rememberSaveable(decorationOffsetX, decorationOffsetY) {
        derivedStateOf {
            Point(
                (location.x + decorationOffsetX).roundToInt(), (location.y + decorationOffsetY).roundToInt()
            )
        }
    }

    val (windowPlacement, setWindowPlacement) = rememberSaveable { mutableStateOf(placement) }

    fun changeWindowPlacement() =
        setWindowPlacement(if (placement == WindowPlacement.Floating) WindowPlacement.Maximized else WindowPlacement.Floating)

    LaunchedEffect(decorationOffset) {
        location = decorationOffset
    }

    LaunchedEffect(windowPlacement) {
        placement = windowPlacement
    }

    Row(modifier = Modifier.fillMaxWidth().height(32.dp).pointerInput(Unit) {
        detectTapGestures(onDoubleTap = { changeWindowPlacement() })
    }.composed {
        if (windowPlacement == WindowPlacement.Floating) pointerInput(Unit) {
            detectDragGestures(onDragStart = {
                decorationOffsetX = 0f
                decorationOffsetY = 0f
            }) { _, (x, y) ->
                decorationOffsetX += x
                decorationOffsetY += y
            }
        } else this
    }, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically
    ) {
        Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                content()
            }
        }
        Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f).clickable {
            isMinimized = !isMinimized
        }, contentAlignment = Alignment.Center) {
            Icon(
                if (isMinimized) Icons.Rounded.Maximize else Icons.Rounded.Minimize,
                "window is minimized or maximized",
                tint = Color.White
            )
        }
        Box(
            modifier = Modifier.fillMaxHeight().aspectRatio(1f).clickable { changeWindowPlacement() },
            contentAlignment = Alignment.Center
        ) {
            Icon(
                if (windowPlacement == WindowPlacement.Maximized) Icons.Rounded.FullscreenExit else Icons.Rounded.Fullscreen,
                "window is floating or fullscreen",
                tint = Color.White
            )
        }
        Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f).clickable {
            exitApplication()
        }, contentAlignment = Alignment.Center) {
            Icon(
                Icons.Rounded.Close, "close window", tint = Color.White
            )
        }
    }
}