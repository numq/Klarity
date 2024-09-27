package renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.skia.*
import kotlin.time.measureTime

private fun createImageFromBytes(
    width: Int,
    height: Int,
    bytes: ByteArray,
    colorType: ColorType = ColorType.RGBA_8888,
    alphaType: ColorAlphaType = ColorAlphaType.PREMUL,
) = runCatching {
    val imageInfo = ImageInfo(
        width = width, height = height, colorType = colorType, alphaType = alphaType
    )

    Image.makeRaster(imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel)
}

/**
 * A composable that renders a [Foreground] onto the screen with an optional [Background].
 * It supports different scaling and rendering configurations.
 *
 * @param modifier The [Modifier] to apply to the composable.
 * @param foreground The [Foreground] content to render.
 * @param background The [Background] content to render behind the [Foreground]. Defaults to [Background.Transparent].
 * @param logRenderingTime If true, logs the time taken to render each frame.
 * @param placeholder A composable to display when the [Foreground] is not yet rendered. Can be null.
 */
@Composable
fun Renderer(
    modifier: Modifier,
    foreground: Foreground,
    background: Background = Background.Transparent,
    logRenderingTime: Boolean = false,
    placeholder: @Composable (BoxWithConstraintsScope.() -> Unit)? = null,
) {
    val coroutineScope = rememberCoroutineScope()

    val backgroundPaint by remember(background) {
        derivedStateOf {
            Paint().apply {
                when (background) {
                    is Background.Transparent -> color = Color.TRANSPARENT

                    is Background.Color -> with(background) { color = Color.makeARGB(a, r, g, b) }

                    is Background.Blur -> with(background) {
                        imageFilter = ImageFilter.makeBlur(sigma, sigma, FilterTileMode.CLAMP)
                    }
                }
            }
        }
    }

    val foregroundImage by remember(foreground) {
        derivedStateOf {
            when (foreground) {
                is Foreground.Source -> with(foreground) {
                    renderer.preview?.run {
                        createImageFromBytes(width, height, bytes).getOrNull()
                    }
                }

                is Foreground.Frame -> with(foreground) {
                    createImageFromBytes(frame.width, frame.height, frame.bytes).getOrNull()
                }

                is Foreground.Cover -> with(foreground) {
                    createImageFromBytes(width, height, bytes, colorType, alphaType).getOrNull()
                }

                is Foreground.Empty -> null
            }
        }
    }

    var renderedImage by remember { mutableStateOf<Image?>(null) }

    DisposableEffect(background) {
        onDispose {
            backgroundPaint.takeIf { !it.isClosed }?.close()
        }
    }

    DisposableEffect(foreground) {
        onDispose {
            foregroundImage?.takeIf { !it.isClosed }?.close()
            try {
                renderedImage?.takeIf { !it.isClosed }?.close()
            } finally {
                renderedImage = null
            }
        }
    }

    DisposableEffect(foreground) {
        (foreground as? Foreground.Source)?.run {
            coroutineScope.launch {
                var renderJob: Job? = null
                foreground.renderer.frame.collectLatest { frame ->
                    renderJob?.cancel()
                    renderJob = launch {
                        val renderingTime = measureTime {
                            val newImage = frame?.let {
                                createImageFromBytes(it.width, it.height, it.bytes).getOrNull()
                            }

                            renderedImage?.takeIf { !it.isClosed }?.close()

                            renderedImage = newImage
                        }

                        if (logRenderingTime) {
                            println("Rendering time: $renderingTime")
                        }
                    }
                }
            }
        }

        onDispose {
            coroutineScope.coroutineContext.cancelChildren()
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        when (val image = (renderedImage ?: foregroundImage)) {
            null -> placeholder?.invoke(this)

            else -> {
                val backgroundSize by remember(maxWidth, maxHeight, background.scale, image.width, image.height) {
                    derivedStateOf {
                        when (background) {
                            is Background.Blur -> background.scale.scaleDp(
                                DpSize(image.width.dp, image.height.dp), DpSize(maxWidth, maxHeight)
                            )

                            else -> DpSize.Zero
                        }
                    }
                }

                val backgroundOffset by remember(maxWidth, maxHeight, backgroundSize.width, backgroundSize.height) {
                    derivedStateOf {
                        when (background) {
                            is Background.Blur -> DpOffset(
                                (maxWidth - backgroundSize.width).div(2f),
                                (maxHeight - backgroundSize.height).div(2f),
                            )

                            else -> DpOffset.Zero
                        }
                    }
                }

                val foregroundSize by remember(maxWidth, maxHeight, foreground.scale, image.width, image.height) {
                    derivedStateOf {
                        foreground.scale.scaleDp(
                            DpSize(image.width.dp, image.height.dp), DpSize(maxWidth, maxHeight)
                        )
                    }
                }

                val foregroundOffset by remember(maxWidth, maxHeight, foregroundSize.width, foregroundSize.height) {
                    derivedStateOf {
                        DpOffset(
                            (maxWidth - foregroundSize.width).div(2f),
                            (maxHeight - foregroundSize.height).div(2f),
                        )
                    }
                }

                Surface {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawIntoCanvas { canvas ->
                            when (background) {
                                is Background.Blur -> {
                                    canvas.nativeCanvas.drawImageRect(
                                        image, Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()), Rect(
                                            backgroundOffset.x.toPx(),
                                            backgroundOffset.y.toPx(),
                                            backgroundOffset.x.toPx() + backgroundSize.width.toPx(),
                                            backgroundOffset.y.toPx() + backgroundSize.height.toPx(),
                                        ), backgroundPaint
                                    )
                                }

                                else -> canvas.nativeCanvas.drawPaint(backgroundPaint)
                            }

                            canvas.nativeCanvas.drawImageRect(
                                image, Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()), Rect(
                                    foregroundOffset.x.toPx(),
                                    foregroundOffset.y.toPx(),
                                    foregroundOffset.x.toPx() + foregroundSize.width.toPx(),
                                    foregroundOffset.y.toPx() + foregroundSize.height.toPx(),
                                ), Paint()
                            )
                        }
                    }
                }
            }
        }
    }
}