package io.github.numq.example.decoration

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.awt.ComposeWindow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.isSpecified
import androidx.compose.ui.input.key.KeyEvent
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.ApplicationScope
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.WindowPosition
import androidx.compose.ui.window.rememberWindowState
import java.awt.*
import java.awt.event.WindowStateListener
import java.lang.ref.WeakReference
import javax.swing.SwingUtilities
import kotlin.math.roundToInt

@Composable
fun ApplicationScope.WindowDecoration(
    isDarkTheme: Boolean,
    setIsDarkTheme: (Boolean) -> Unit = {},
    windowDecorationHeight: Dp = 32.dp,
    windowDecorationColors: WindowDecorationColors = WindowDecorationColors(),
    initialWindowPosition: WindowPosition? = null,
    initialWindowSize: DpSize? = null,
    minimumWindowSize: DpSize? = null,
    isVisible: Boolean = true,
    isTransparent: Boolean = true,
    isResizable: Boolean = true,
    isEnabled: Boolean = true,
    isFocusable: Boolean = true,
    isAlwaysOnTop: Boolean = false,
    onPreviewKeyEvent: (KeyEvent) -> Boolean = { false },
    onKeyEvent: (KeyEvent) -> Boolean = { false },
    onCloseRequest: () -> Unit = ::exitApplication,
    title: @Composable RowScope.() -> Unit,
    controls: @Composable RowScope.() -> Unit = {},
    content: @Composable (ComposeWindow.(WindowDecorationState) -> Unit),
) {
    initialWindowPosition?.run {
        require(x >= 0.dp && y >= 0.dp) { "Initial window position must be positive" }
    }

    initialWindowSize?.run {
        require(width >= 0.dp && height >= 0.dp) { "Initial window size must be positive" }
    }

    minimumWindowSize?.run {
        require(width >= 0.dp && height >= 0.dp) { "Minimum window size must be positive" }
    }

    val windowState = rememberWindowState(
        position = initialWindowPosition ?: WindowPosition.PlatformDefault,
        size = (initialWindowSize ?: DpSize(800.dp, 600.dp)).run {
            copy(
                width = width.coerceAtLeast(minimumWindowSize?.width ?: width),
                height = height.coerceAtLeast(minimumWindowSize?.height ?: height)
            )
        })

    var isMinimized by remember { mutableStateOf(false) }

    var isFullscreen by remember { mutableStateOf(false) }

    val state = remember(windowState, isMinimized, isFullscreen) {
        WindowDecorationState(
            size = windowState.size,
            position = DpOffset(x = windowState.position.x, y = windowState.position.y),
            isMinimized = isMinimized,
            isFullscreen = isFullscreen,
        )
    }

    Window(
        onCloseRequest = onCloseRequest,
        state = windowState,
        visible = isVisible,
        undecorated = true,
        transparent = isTransparent,
        resizable = isResizable,
        enabled = isEnabled,
        focusable = isFocusable,
        alwaysOnTop = isAlwaysOnTop,
        onPreviewKeyEvent = onPreviewKeyEvent,
        onKeyEvent = onKeyEvent,
    ) {
        val windowRef = remember { WeakReference(window) }

        var lastWindowBounds by remember { mutableStateOf<Rectangle?>(null) }

        var lastWindowLocation by remember { mutableStateOf<Point?>(null) }

        DisposableEffect(Unit) {
            val minimizationListener = WindowStateListener { event ->
                isMinimized = (event.newState and Frame.ICONIFIED) != 0
            }

            val window = windowRef.get()

            val scaleX = window?.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0

            val scaleY = window?.graphicsConfiguration?.defaultTransform?.scaleY ?: 1.0

            val minimumWidth = (minimumWindowSize?.width?.value?.times(scaleX)?.toInt() ?: 1).coerceAtLeast(1)

            val minimumHeight = (minimumWindowSize?.height?.value?.times(scaleY)?.toInt() ?: 1).coerceAtLeast(1)

            window?.minimumSize = Dimension(minimumWidth, minimumHeight)

            window?.addWindowStateListener(minimizationListener)

            onDispose {
                window?.removeWindowStateListener(minimizationListener)
            }
        }

        LaunchedEffect(minimumWindowSize, isFullscreen) {
            val window = windowRef.get() ?: return@LaunchedEffect

            SwingUtilities.invokeLater {
                if (!window.isVisible) return@invokeLater

                val scaleX = window.graphicsConfiguration?.defaultTransform?.scaleX ?: 1.0

                val scaleY = window.graphicsConfiguration?.defaultTransform?.scaleY ?: 1.0

                val minimumWidth = (minimumWindowSize?.width?.value?.times(scaleX)?.toInt() ?: 1).coerceAtLeast(1)

                val minimumHeight = (minimumWindowSize?.height?.value?.times(scaleY)?.toInt() ?: 1).coerceAtLeast(1)

                window.minimumSize = Dimension(minimumWidth, minimumHeight)

                if (!isFullscreen) {
                    val newWidth = window.width.coerceAtLeast(minimumWidth)

                    val newHeight = window.height.coerceAtLeast(minimumHeight)

                    if (window.width != newWidth || window.height != newHeight) {
                        window.setSize(newWidth, newHeight)
                    }
                }
            }
        }

        LaunchedEffect(isFullscreen) {
            SwingUtilities.invokeLater {
                if (!window.isVisible) return@invokeLater

                when {
                    isFullscreen && window.bounds.width > 0 && window.bounds.height > 0 -> {
                        lastWindowBounds = window.bounds

                        val windowCenter = Point(window.x + window.width / 2, window.y + window.height / 2)

                        val environment = GraphicsEnvironment.getLocalGraphicsEnvironment()

                        val device = environment.screenDevices.firstOrNull { devices ->
                            devices.defaultConfiguration.bounds.contains(windowCenter)
                        } ?: environment.defaultScreenDevice

                        val config = device.defaultConfiguration

                        val insets = Toolkit.getDefaultToolkit().getScreenInsets(config)

                        val bounds = config.bounds

                        window.bounds = Rectangle(
                            bounds.x + insets.left,
                            bounds.y + insets.top,
                            bounds.width - insets.left - insets.right,
                            bounds.height - insets.top - insets.bottom
                        )
                    }

                    !isFullscreen -> lastWindowBounds?.takeIf { bounds ->
                        bounds.width > 0 && bounds.height > 0
                    }?.let { bounds ->
                        window.bounds = bounds
                    }
                }
            }
        }

        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val contentHeight = remember(maxHeight, windowDecorationHeight) { maxHeight - windowDecorationHeight }

            Column(modifier = Modifier.fillMaxSize()) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(windowDecorationHeight)
                        .background(windowDecorationColors.decoration()).pointerInput(Unit) {
                            detectTapGestures(onDoubleTap = {
                                isFullscreen = !isFullscreen
                            })
                        }.pointerInput(Unit) {
                            var dragOffset = Offset.Zero

                            detectDragGestures(onDragStart = { initialOffset ->
                                lastWindowLocation = window.location

                                dragOffset = initialOffset
                            }, onDragCancel = {
                                lastWindowLocation?.let { location ->
                                    window.location = location
                                }
                            }, onDragEnd = {
                                lastWindowLocation = window.location
                            }) { change, _ ->
                                val dx = (change.position.x - dragOffset.x).roundToInt()

                                val dy = (change.position.y - dragOffset.y).roundToInt()

                                SwingUtilities.invokeLater {
                                    window.location = window.location.apply { translate(dx, dy) }
                                }
                            }
                        },
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        horizontalArrangement = Arrangement.Start,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        title()
                    }
                    Row(
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        controls()

                        windowDecorationColors.switchSchemeButton().takeIf(Color::isSpecified)?.let { tint ->
                            Box(
                                modifier = Modifier.aspectRatio(1f).clickable {
                                    setIsDarkTheme(!isDarkTheme)
                                }, contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                                    null,
                                    tint = tint
                                )
                            }
                        }

                        windowDecorationColors.minimizeButton().takeIf(Color::isSpecified)?.let { tint ->
                            Box(
                                modifier = Modifier.aspectRatio(1f).clickable {
                                    SwingUtilities.invokeLater {
                                        window.extendedState = Frame.ICONIFIED
                                    }
                                }, contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    Icons.Default.Minimize, null, tint = tint
                                )
                            }
                        }

                        windowDecorationColors.fullscreenButton().takeIf(Color::isSpecified)?.let { tint ->
                            Box(
                                modifier = Modifier.aspectRatio(1f).clickable {
                                    isFullscreen = !isFullscreen
                                }, contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                                    null,
                                    tint = tint
                                )
                            }
                        }

                        windowDecorationColors.closeButton().takeIf(Color::isSpecified)?.let { tint ->
                            Box(
                                modifier = Modifier.aspectRatio(1f).clickable {
                                    onCloseRequest()
                                }, contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Default.Close, null, tint = tint)
                            }
                        }
                    }
                }

                Box(
                    modifier = Modifier.fillMaxWidth().height(contentHeight)
                        .background(windowDecorationColors.content())
                ) {
                    content(window, state)
                }
            }
        }
    }
}