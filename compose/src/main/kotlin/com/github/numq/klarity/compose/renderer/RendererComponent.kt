package com.github.numq.klarity.compose.renderer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
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
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import org.jetbrains.skia.*

@Composable
fun RendererComponent(
    modifier: Modifier = Modifier,
    foreground: Foreground,
    background: Background = Background.Transparent,
    placeholder: @Composable (BoxWithConstraintsScope.() -> Unit)? = null,
) {
    when (foreground) {
        is Foreground.Empty -> Box(modifier = modifier)

        else -> {
            val imageInfo = remember(foreground) {
                ImageInfo(
                    width = when (foreground) {
                        is Foreground.Source -> foreground.renderer.format.width

                        is Foreground.Frame -> foreground.frame.width

                        is Foreground.Image -> foreground.width

                        else -> 0
                    }, height = when (foreground) {
                        is Foreground.Source -> foreground.renderer.format.height

                        is Foreground.Frame -> foreground.frame.height

                        is Foreground.Image -> foreground.height

                        else -> 0
                    }, colorType = ColorType.RGBA_8888, alphaType = ColorAlphaType.UNPREMUL
                )
            }

            val bitmap = remember(imageInfo) {
                Bitmap().apply {
                    if (!allocPixels(imageInfo)) {
                        close()
                        error("Could not allocate bitmap pixels")
                    }
                }
            }

            val surface = remember(imageInfo) {
                Surface.makeRaster(imageInfo)
            }

            var generationId by remember {
                mutableStateOf(0)
            }

            LaunchedEffect(foreground) {
                when (foreground) {
                    is Foreground.Source -> {
                        foreground.renderer.frame.filterNotNull().collectLatest { frame ->
                            if (bitmap.installPixels(frame.bytes)) {
                                surface.canvas.writePixels(bitmap, 0, 0)

                                generationId = bitmap.generationId
                            }
                        }
                    }

                    else -> Unit
                }
            }

            DisposableEffect(foreground) {
                when (foreground) {
                    is Foreground.Source -> if (bitmap.installPixels(foreground.renderer.preview?.bytes)) {
                        surface.canvas.writePixels(bitmap, 0, 0)

                        generationId = bitmap.generationId
                    }

                    is Foreground.Frame -> if (bitmap.installPixels(foreground.frame.bytes)) {
                        surface.canvas.writePixels(bitmap, 0, 0)

                        generationId = bitmap.generationId
                    }

                    is Foreground.Image -> if (bitmap.installPixels(foreground.bytes)) {
                        surface.canvas.writePixels(bitmap, 0, 0)

                        generationId = bitmap.generationId
                    }

                    else -> Unit
                }

                onDispose {
                    generationId = 0
                }
            }

            DisposableEffect(imageInfo) {
                onDispose {
                    if (!surface.isClosed) {
                        surface.close()
                    }

                    if (!bitmap.isClosed) {
                        bitmap.close()
                    }
                }
            }

            Surface(modifier = modifier) {
                BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    val size = remember(maxWidth, maxHeight) {
                        Size(maxWidth.value, maxHeight.value)
                    }

                    val foregroundSize = remember(foreground, size) {
                        calculateScaledSize(foreground, size)
                    }

                    val foregroundOffset = remember(foregroundSize, size) {
                        calculateOffset(foregroundSize, size)
                    }

                    val backgroundSize = remember(background, size, foreground) {
                        calculateBackgroundSize(background, size, foreground)
                    }

                    val backgroundOffset = remember(backgroundSize, size) {
                        calculateBackgroundOffset(backgroundSize, size)
                    }

                    key(backgroundSize, foregroundSize, generationId) {
                        Canvas(modifier = modifier.fillMaxSize()) {
                            drawIntoCanvas { canvas ->
                                drawBackground(background, backgroundSize, backgroundOffset, surface, canvas)

                                drawForeground(foregroundSize, foregroundOffset, surface, canvas)
                            }
                        }
                    }

                    if (bitmap.drawsNothing()) {
                        placeholder?.invoke(this)
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

private fun drawForeground(
    foregroundSize: Size,
    foregroundOffset: Offset,
    surface: Surface,
    canvas: androidx.compose.ui.graphics.Canvas,
) {
    canvas.withSave {
        canvas.translate(foregroundOffset.x, foregroundOffset.y)

        canvas.scale(foregroundSize.width / surface.width, foregroundSize.height / surface.height)

        surface.draw(canvas.nativeCanvas, 0, 0, null)
    }
}