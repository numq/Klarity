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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.withSave
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import org.jetbrains.skia.FilterBlurMode
import org.jetbrains.skia.MaskFilter
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Surface

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
                    var renderJob by remember { mutableStateOf<Job?>(null) }

                    val rendererContext = remember(foreground) { RendererContext.create(foreground) }

                    var generationId by remember { mutableStateOf(0) }

                    LaunchedEffect(rendererContext) {
                        renderJob?.cancelAndJoin()
                        renderJob = launch {
                            when (foreground) {
                                is Foreground.Source -> foreground.renderer.frame.collect { frame ->
                                    rendererContext.draw(frame.bytes) {
                                        generationId = it
                                    }
                                }

                                is Foreground.Frame -> rendererContext.draw(foreground.frame.bytes) {
                                    generationId = it
                                }

                                is Foreground.Image -> rendererContext.draw(foreground.bytes) {
                                    generationId = it
                                }

                                else -> return@launch
                            }
                        }
                    }

                    DisposableEffect(rendererContext) {
                        onDispose {
                            renderJob?.cancel()

                            renderJob = null

                            rendererContext.close()

                            generationId = 0
                        }
                    }

                    val size = remember(maxWidth, maxHeight) {
                        Size(maxWidth.value, maxHeight.value)
                    }

                    val foregroundSize by remember(foreground, size) {
                        derivedStateOf {
                            calculateScaledSize(foreground, size)
                        }
                    }

                    val foregroundOffset by remember(foregroundSize, size) {
                        derivedStateOf {
                            calculateOffset(foregroundSize, size)
                        }
                    }

                    val backgroundSize by remember(background, size, foreground) {
                        derivedStateOf {
                            calculateBackgroundSize(background, size, foreground)
                        }
                    }

                    val backgroundOffset by remember(backgroundSize, size) {
                        derivedStateOf {
                            calculateBackgroundOffset(backgroundSize, size)
                        }
                    }

                    key(generationId) {
                        Canvas(modifier = modifier.fillMaxSize()) {
                            drawIntoCanvas { canvas ->
                                drawBackground(
                                    background = background,
                                    backgroundSize = backgroundSize,
                                    backgroundOffset = backgroundOffset,
                                    surface = rendererContext.surface,
                                    canvas = canvas
                                )

                                drawForeground(
                                    foregroundSize = foregroundSize,
                                    foregroundOffset = foregroundOffset,
                                    surface = rendererContext.surface,
                                    canvas = canvas
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun calculateScaledSize(foreground: Foreground, size: Size) = foreground.imageScale.scale(
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
    canvas: androidx.compose.ui.graphics.Canvas,
) {
    canvas.withSave {
        when (background) {
            is Background.Transparent -> Unit

            is Background.Color -> drawRect(color = with(background) { Color(red = r, green = g, blue = b, alpha = a) })

            is Background.Blur -> {
                canvas.translate(backgroundOffset.x, backgroundOffset.y)

                if (!surface.isClosed) {
                    canvas.scale(backgroundSize.width / surface.width, backgroundSize.height / surface.height)

                    surface.draw(canvas.nativeCanvas, 0, 0, Paint().apply {
                        maskFilter = MaskFilter.makeBlur(
                            mode = FilterBlurMode.NORMAL, sigma = background.sigma
                        )
                    })
                }
            }
        }
    }
}

private fun drawForeground(
    foregroundSize: Size,
    foregroundOffset: Offset,
    surface: Surface,
    canvas: androidx.compose.ui.graphics.Canvas,
) {
    canvas.withSave {
        canvas.translate(foregroundOffset.x, foregroundOffset.y)

        if (!surface.isClosed) {
            canvas.scale(foregroundSize.width / surface.width, foregroundSize.height / surface.height)

            surface.draw(canvas.nativeCanvas, 0, 0, null)
        }
    }
}