package io.github.numq.klarity.renderer.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import org.jetbrains.skia.*

@Composable
fun RendererComponent(
    modifier: Modifier = Modifier,
    foreground: Foreground,
    background: Background = Background.Transparent,
    placeholder: @Composable () -> Unit = {},
) {
    val drawsNothing by foreground.renderer.drawsNothing.collectAsState()

    val generationId by foreground.renderer.generationId.collectAsState()

    val backgroundPaint = remember(background) {
        when (background) {
            is Background.Transparent -> null

            is Background.Color -> Paint().apply {
                color = with(background) {
                    Color.makeARGB(a = alpha, r = red, g = green, b = blue)
                }
            }

            is Background.Blur -> Paint().apply {
                imageFilter = with(background) {
                    ImageFilter.makeBlur(sigmaX = sigmaX, sigmaY = sigmaY, mode = FilterTileMode.CLAMP)
                }
            }
        }
    }

    DisposableEffect(backgroundPaint) {
        onDispose {
            backgroundPaint?.close()
        }
    }

    Box(modifier = modifier.fillMaxSize().clipToBounds(), contentAlignment = Alignment.Center) {
        when {
            drawsNothing -> placeholder()

            else -> key(generationId) {
                Box(modifier = Modifier.fillMaxSize().drawWithCache {
                    val dstSize = size

                    val srcSize = Size(
                        width = foreground.renderer.width.toFloat(), height = foreground.renderer.height.toFloat()
                    )

                    val foregroundSize = foreground.imageScale.scale(srcSize, dstSize)

                    val foregroundOffset = Offset(
                        x = (dstSize.width - foregroundSize.width) / 2f,
                        y = (dstSize.height - foregroundSize.height) / 2f
                    )

                    val foregroundRect = Rect.makeXYWH(
                        l = foregroundOffset.x,
                        t = foregroundOffset.y,
                        w = foregroundSize.width,
                        h = foregroundSize.height
                    )

                    val backgroundRect = when {
                        background is Background.Blur -> {
                            val backgroundSize = background.imageScale.scale(srcSize, dstSize)

                            val backgroundOffset = Offset(
                                x = (dstSize.width - backgroundSize.width) / 2f,
                                y = (dstSize.height - backgroundSize.height) / 2f
                            )

                            Rect.makeXYWH(
                                l = backgroundOffset.x,
                                t = backgroundOffset.y,
                                w = backgroundSize.width,
                                h = backgroundSize.height
                            )

                        }

                        else -> foregroundRect
                    }

                    onDrawBehind {
                        drawIntoCanvas { canvas ->
                            foreground.renderer.onRender(
                                canvas = canvas.nativeCanvas,
                                backgroundRect = backgroundRect,
                                backgroundColorPaint = backgroundPaint.takeIf { background is Background.Color },
                                backgroundBlurPaint = backgroundPaint.takeIf { background is Background.Blur },
                                foregroundRect = foregroundRect
                            )
                        }
                    }
                })
            }
        }
    }
}