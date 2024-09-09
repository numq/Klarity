package decoration

import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.composed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.WindowPlacement
import java.awt.Point
import kotlin.math.roundToInt

@Composable
fun DecorationBox(
    window: ComposeWindow,
    isDarkTheme: Boolean,
    changeTheme: (isDarkTheme: Boolean) -> Unit,
    close: () -> Unit,
    content: @Composable RowScope.() -> Unit,
) {
    var decorationOffsetX by remember { mutableStateOf(0f) }
    var decorationOffsetY by remember { mutableStateOf(0f) }
    val decorationOffset by remember(decorationOffsetX, decorationOffsetY) {
        derivedStateOf {
            Point(
                (window.location.x + decorationOffsetX).roundToInt(),
                (window.location.y + decorationOffsetY).roundToInt()
            )
        }
    }

    LaunchedEffect(decorationOffset) {
        window.location = decorationOffset
    }

    Row(modifier = Modifier.fillMaxWidth().height(32.dp).pointerInput(Unit) {
        detectTapGestures(onDoubleTap = {
            window.placement =
                if (window.placement == WindowPlacement.Floating) WindowPlacement.Maximized else WindowPlacement.Floating
        })
    }.composed {
        if (window.placement == WindowPlacement.Floating) pointerInput(Unit) {
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
            changeTheme(!isDarkTheme)
        }, contentAlignment = Alignment.Center) {
            Icon(
                if (isDarkTheme) Icons.Default.DarkMode else Icons.Default.LightMode,
                "light or dark theme",
                tint = MaterialTheme.colors.primary
            )
        }
        Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f).clickable {
            window.isMinimized = !window.isMinimized
        }, contentAlignment = Alignment.Center) {
            Icon(
                if (window.isMinimized) Icons.Default.Maximize else Icons.Default.Minimize,
                "window is minimized or maximized",
                tint = MaterialTheme.colors.primary
            )
        }
        Box(
            modifier = Modifier.fillMaxHeight().aspectRatio(1f).clickable {
                window.placement =
                    if (window.placement == WindowPlacement.Floating) WindowPlacement.Maximized else WindowPlacement.Floating
            }, contentAlignment = Alignment.Center
        ) {
            Icon(
                if (window.placement == WindowPlacement.Maximized) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                "window is floating or fullscreen",
                tint = MaterialTheme.colors.primary
            )
        }
        Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f).clickable {
            close()
        }, contentAlignment = Alignment.Center) {
            Icon(Icons.Default.Close, "close window", tint = MaterialTheme.colors.primary)
        }
    }
}