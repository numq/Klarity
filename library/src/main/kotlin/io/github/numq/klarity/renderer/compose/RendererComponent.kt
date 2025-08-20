package io.github.numq.klarity.renderer.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.withSave
import org.jetbrains.skia.FilterTileMode
import org.jetbrains.skia.ImageFilter
import org.jetbrains.skia.Surface
import org.jetbrains.skia.Paint as SkPaint

@Composable
fun RendererComponent(
    modifier: Modifier = Modifier,
    foreground: Foreground,
    background: Background = Background.Transparent,
    placeholder: @Composable () -> Unit = {},
) {
    val generationId by foreground.renderer.generationId.collectAsState()

    val drawsNothing by foreground.renderer.drawsNothing.collectAsState()

    BoxWithConstraints(modifier = modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        val boxSize = Size(width = maxWidth.value, height = maxHeight.value)

        val foregroundSize by remember(
            foreground.renderer.width, foreground.renderer.height, foreground.imageScale, boxSize
        ) {
            derivedStateOf {
                foreground.imageScale.scale(
                    srcSize = Size(
                        width = foreground.renderer.width.toFloat(), height = foreground.renderer.height.toFloat()
                    ), dstSize = boxSize
                )
            }
        }

        val foregroundOffset by remember(foregroundSize, boxSize) {
            derivedStateOf {
                calculateOffset(foregroundSize, boxSize)
            }
        }

        val backgroundSize by remember(background, boxSize, foregroundSize) {
            derivedStateOf {
                calculateBackgroundSize(background, boxSize, foregroundSize)
            }
        }

        val backgroundOffset by remember(backgroundSize, boxSize) {
            derivedStateOf {
                calculateBackgroundOffset(backgroundSize, boxSize)
            }
        }

        key(generationId, drawsNothing) {
            when {
                drawsNothing -> placeholder()

                else -> Canvas(modifier = modifier.fillMaxSize()) {
                    foreground.renderer.draw { surface ->
                        drawBackground(
                            background = background,
                            backgroundSize = backgroundSize,
                            backgroundOffset = backgroundOffset,
                            surface = surface,
                        )
                        drawForeground(
                            foregroundSize = foregroundSize,
                            foregroundOffset = foregroundOffset,
                            surface = surface,
                        )
                    }
                }
            }
        }
    }
}

private fun calculateOffset(scaledSize: Size, size: Size) = Offset(
    x = (size.width - scaledSize.width) / 2f, y = (size.height - scaledSize.height) / 2f
)

private fun calculateBackgroundSize(background: Background, size: Size, foregroundSize: Size) = when (background) {
    is Background.Blur -> background.imageScale.scale(srcSize = foregroundSize, dstSize = size)

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
) {
    when (background) {
        is Background.Transparent -> Unit

        is Background.Color -> drawRect(color = with(background) { Color(red = r, green = g, blue = b, alpha = a) })

        is Background.Blur -> drawIntoCanvas { canvas ->
            if (surface.isClosed || canvas.nativeCanvas.isClosed || surface.width <= 0 || surface.height <= 0) {
                return@drawIntoCanvas
            }

            canvas.withSave {
                canvas.translate(backgroundOffset.x, backgroundOffset.y)

                canvas.scale(backgroundSize.width / surface.width, backgroundSize.height / surface.height)

                surface.draw(canvas.nativeCanvas, 0, 0, SkPaint().apply {
                    imageFilter = ImageFilter.makeBlur(
                        sigmaX = background.sigma, sigmaY = background.sigma, mode = FilterTileMode.CLAMP
                    )
                })
            }
        }
    }
}

private fun DrawScope.drawForeground(
    foregroundSize: Size,
    foregroundOffset: Offset,
    surface: Surface,
) {
    drawIntoCanvas { canvas ->
        if (surface.isClosed || canvas.nativeCanvas.isClosed || surface.width <= 0 || surface.height <= 0) {
            return@drawIntoCanvas
        }

        canvas.withSave {
            canvas.translate(foregroundOffset.x, foregroundOffset.y)

            canvas.scale(foregroundSize.width / surface.width, foregroundSize.height / surface.height)

            surface.draw(canvas.nativeCanvas, 0, 0, null)
        }
    }
}