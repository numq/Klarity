package com.github.numq.klarity.compose.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.withSave
import kotlinx.coroutines.launch
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Paint as SkPaint

@Composable
fun RendererComponent(
    modifier: Modifier = Modifier,
    foreground: Foreground,
    background: Background = Background.Transparent,
    placeholder: @Composable (BoxWithConstraintsScope.() -> Unit)? = null,
) {
    Surface(modifier = modifier) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            when (foreground) {
                is Foreground.Empty -> placeholder?.invoke(this)

                else -> {
                    val rendererScope = rememberCoroutineScope()

                    val rendererContext = remember(
                        foreground.width,
                        foreground.height,
                        foreground.colorType,
                        foreground.alphaType
                    ) { RendererContext.create(foreground) }

                    val generationId by rendererContext.generationId.collectAsState(0)

                    val boxSize = remember(maxWidth, maxHeight) {
                        Size(maxWidth.value, maxHeight.value)
                    }

                    val foregroundSize by remember(
                        foreground.width, foreground.height, foreground.imageScale, boxSize
                    ) {
                        derivedStateOf {
                            foreground.imageScale.scale(
                                srcSize = Size(foreground.width.toFloat(), foreground.height.toFloat()),
                                dstSize = boxSize
                            )
                        }
                    }

                    val foregroundOffset by remember(foregroundSize, boxSize) {
                        derivedStateOf {
                            calculateOffset(foregroundSize, boxSize)
                        }
                    }

                    val backgroundSize by remember(background, boxSize, foreground) {
                        derivedStateOf {
                            calculateBackgroundSize(background, boxSize, foreground)
                        }
                    }

                    val backgroundOffset by remember(backgroundSize, boxSize) {
                        derivedStateOf {
                            calculateBackgroundOffset(backgroundSize, boxSize)
                        }
                    }

                    DisposableEffect(foreground) {
                        val renderJob = rendererScope.launch {
                            when (foreground) {
                                is Foreground.Source -> foreground.renderer.frame.collect { frame ->
                                    frame?.bytes?.let(rendererContext::draw) ?: rendererContext.reset()
                                }

                                is Foreground.Frame -> rendererContext.draw(foreground.frame.bytes)

                                is Foreground.Image -> rendererContext.draw(foreground.bytes)

                                else -> Unit
                            }
                        }

                        onDispose {
                            renderJob.cancel()
                        }
                    }

                    DisposableEffect(rendererContext) {
                        onDispose {
                            rendererContext.close()
                        }
                    }

                    key(generationId) {
                        Canvas(modifier = modifier.fillMaxSize()) {
                            rendererContext.withSurface { surface ->
                                if (!surface.isClosed) {
                                    drawBackground(
                                        background = background,
                                        backgroundSize = backgroundSize,
                                        backgroundOffset = backgroundOffset,
                                        surface = surface,
                                        canvas = drawContext.canvas
                                    )

                                    drawForeground(
                                        foregroundSize = foregroundSize,
                                        foregroundOffset = foregroundOffset,
                                        surface = surface,
                                        canvas = drawContext.canvas
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateOffset(scaledSize: Size, size: Size) = Offset(
    x = (size.width - scaledSize.width) / 2f, y = (size.height - scaledSize.height) / 2f
)

private fun calculateBackgroundSize(background: Background, size: Size, foreground: Foreground) = when (background) {
    is Background.Blur -> background.imageScale.scale(
        srcSize = Size(
            width = when (foreground) {
                is Foreground.Source -> foreground.renderer.format.width

                is Foreground.Frame -> foreground.frame.width

                is Foreground.Image -> foreground.width

                else -> 0
            }.toFloat(), height = when (foreground) {
                is Foreground.Source -> foreground.renderer.format.height

                is Foreground.Frame -> foreground.frame.height

                is Foreground.Image -> foreground.height

                else -> 0
            }.toFloat()
        ), dstSize = size
    )

    else -> Size.Zero
}

private fun calculateBackgroundOffset(backgroundSize: Size, size: Size) = if (backgroundSize != Size.Zero) {
    Offset(x = (size.width - backgroundSize.width) / 2f, y = (size.height - backgroundSize.height) / 2f)
} else {
    Offset.Zero
}

private fun DrawScope.drawBackground(
    background: Background,
    backgroundSize: Size,
    backgroundOffset: Offset,
    surface: Surface,
    canvas: Canvas,
) {
    when (background) {
        is Background.Transparent -> Unit

        is Background.Color -> drawRect(color = with(background) { Color(red = r, green = g, blue = b, alpha = a) })

        is Background.Blur -> canvas.withSave {
            canvas.translate(backgroundOffset.x, backgroundOffset.y)

            canvas.scale(backgroundSize.width / surface.width, backgroundSize.height / surface.height)

            surface.draw(canvas.nativeCanvas, 0, 0, SkPaint().apply {
                maskFilter = MaskFilter.makeBlur(
                    mode = FilterBlurMode.NORMAL,
                    sigma = background.sigma
                )
            })
        }
    }
}

private fun drawForeground(
    foregroundSize: Size,
    foregroundOffset: Offset,
    surface: Surface,
    canvas: Canvas,
) {
    canvas.withSave {
        canvas.translate(foregroundOffset.x, foregroundOffset.y)

        canvas.scale(foregroundSize.width / surface.width, foregroundSize.height / surface.height)

        surface.draw(canvas.nativeCanvas, 0, 0, null)
    }
}