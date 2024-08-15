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
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import org.jetbrains.skia.*
import kotlin.system.measureTimeMillis

@Composable
fun Renderer(
    modifier: Modifier,
    foreground: Foreground,
    background: Background = Background.Transparent,
    logRenderingTime: Boolean = false,
    placeholder: @Composable (BoxWithConstraintsScope.() -> Unit)? = null,
) {
    val backgroundPaint = remember(background) {
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

    val (frameImage, setFrameImage) = remember { mutableStateOf<Image?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            if (!backgroundPaint.isClosed) backgroundPaint.close()
            if (frameImage?.isClosed == false) frameImage.close()
        }
    }

    LaunchedEffect(foreground) {
        withContext(Dispatchers.Default) {
            when (foreground) {
                is Foreground.Source -> with(foreground) {
                    renderer.frame.map { frame -> frame ?: renderer.preview }.filterNotNull().collectLatest { frame ->
                        val timeTaken = measureTimeMillis {
                            with(frame) {
                                val imageInfo = ImageInfo(
                                    width = width,
                                    height = height,
                                    colorType = ColorType.RGB_565,
                                    alphaType = ColorAlphaType.OPAQUE
                                )

                                if (frameImage?.isClosed == false) frameImage.close()

                                setFrameImage(
                                    Image.makeRaster(
                                        imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel
                                    )
                                )
                            }
                        }
                        if (logRenderingTime) println("Rendering time: $timeTaken ms")
                    }
                }

                is Foreground.Frame -> with(foreground) {
                    val imageInfo = ImageInfo(
                        width = frame.width,
                        height = frame.height,
                        colorType = ColorType.RGB_565,
                        alphaType = ColorAlphaType.OPAQUE
                    )

                    if (frameImage?.isClosed == false) frameImage.close()

                    setFrameImage(
                        Image.makeRaster(
                            imageInfo, frame.bytes, imageInfo.width * imageInfo.bytesPerPixel
                        )
                    )
                }

                is Foreground.Cover -> with(foreground) {
                    val imageInfo = ImageInfo(
                        width = width, height = height, colorType = colorType, alphaType = alphaType
                    )

                    if (frameImage?.isClosed == false) frameImage.close()

                    setFrameImage(
                        Image.makeRaster(
                            imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel
                        )
                    )
                }

                is Foreground.Empty -> Unit
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            frameImage != null -> {
                val backgroundSize = remember(
                    background.scale,
                    frameImage.width,
                    frameImage.height,
                    maxWidth,
                    maxHeight
                ) {
                    when (background) {
                        is Background.Blur -> background.scale.scaleDp(
                            DpSize(frameImage.width.dp, frameImage.height.dp),
                            DpSize(maxWidth, maxHeight)
                        )

                        else -> DpSize.Zero
                    }
                }

                val backgroundOffset = remember(backgroundSize.width, backgroundSize.height, maxWidth, maxHeight) {
                    when (background) {
                        is Background.Blur -> DpOffset(
                            (maxWidth - backgroundSize.width).div(2f),
                            (maxHeight - backgroundSize.height).div(2f),
                        )

                        else -> DpOffset.Zero
                    }
                }

                val foregroundSize = remember(
                    frameImage.width,
                    frameImage.height,
                    foreground.scale,
                    maxWidth,
                    maxHeight
                ) {
                    foreground.scale.scaleDp(
                        DpSize(frameImage.width.dp, frameImage.height.dp), DpSize(maxWidth, maxHeight)
                    )
                }

                val foregroundOffset = remember(foregroundSize.width, foregroundSize.height, maxWidth, maxHeight) {
                    DpOffset(
                        (maxWidth - foregroundSize.width).div(2f),
                        (maxHeight - foregroundSize.height).div(2f),
                    )
                }

                Surface {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawIntoCanvas { canvas ->
                            when (background) {
                                is Background.Blur -> {
                                    canvas.nativeCanvas.drawImageRect(
                                        frameImage,
                                        Rect(
                                            0f,
                                            0f,
                                            frameImage.width.toFloat(),
                                            frameImage.height.toFloat()
                                        ),
                                        Rect(
                                            backgroundOffset.x.toPx(),
                                            backgroundOffset.y.toPx(),
                                            backgroundOffset.x.toPx() + backgroundSize.width.toPx(),
                                            backgroundOffset.y.toPx() + backgroundSize.height.toPx(),
                                        ),
                                        backgroundPaint
                                    )
                                }

                                else -> canvas.nativeCanvas.drawPaint(backgroundPaint)
                            }

                            canvas.nativeCanvas.drawImageRect(
                                frameImage,
                                Rect(
                                    0f,
                                    0f,
                                    frameImage.width.toFloat(),
                                    frameImage.height.toFloat()
                                ),
                                Rect(
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

            else -> placeholder?.invoke(this)
        }
    }
}