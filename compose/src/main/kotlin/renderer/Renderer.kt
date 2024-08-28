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
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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
    val mutex = remember { Mutex() }

    val coroutineContext = remember { Dispatchers.Default + SupervisorJob() }

    val renderScope = rememberCoroutineScope()

    var foregroundRendererJob by remember { mutableStateOf<Job?>(null) }

    var foregroundRenderJob by remember { mutableStateOf<Job?>(null) }

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

    var isRendering by remember { mutableStateOf(false) }

    val (frameImage, setFrameImage) = remember { mutableStateOf<Image?>(null) }

    val foregroundImage = remember(foreground) {
        when (foreground) {
            is Foreground.Source -> with(foreground) {
                renderer.preview?.run {
                    val imageInfo = ImageInfo(
                        width = width,
                        height = height,
                        colorType = ColorType.RGBA_8888,
                        alphaType = ColorAlphaType.PREMUL
                    )

                    if (frameImage?.isClosed == false) frameImage.close()

                    Image.makeRaster(
                        imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel
                    )
                }
            }

            is Foreground.Frame -> with(foreground) {
                val imageInfo = ImageInfo(
                    width = frame.width,
                    height = frame.height,
                    colorType = ColorType.RGBA_8888,
                    alphaType = ColorAlphaType.PREMUL
                )

                Image.makeRaster(
                    imageInfo, frame.bytes, imageInfo.width * imageInfo.bytesPerPixel
                )
            }

            is Foreground.Cover -> with(foreground) {
                val imageInfo = ImageInfo(
                    width = width, height = height, colorType = colorType, alphaType = alphaType
                )

                Image.makeRaster(
                    imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel
                )
            }

            is Foreground.Empty -> null
        }
    }

    val image = remember(isRendering, foregroundImage, frameImage) { if (isRendering) frameImage else foregroundImage }

    DisposableEffect(Unit) {
        onDispose {
            if (!backgroundPaint.isClosed) backgroundPaint.close()

            if (frameImage?.isClosed == false) frameImage.close()

            renderScope.cancel()
        }
    }

    DisposableEffect(foreground) {
        when (foreground) {
            is Foreground.Source -> with(foreground) {
                foregroundRendererJob?.cancel()
                foregroundRendererJob = renderScope.launch(coroutineContext) {
                    renderer.frame.filterNotNull().collectLatest { frame ->
                        foregroundRenderJob?.cancel()
                        foregroundRenderJob = launch {
                            mutex.withLock {
                                isRendering = true
                                val timeTaken = measureTimeMillis {
                                    with(frame) {
                                        val imageInfo = ImageInfo(
                                            width = width,
                                            height = height,
                                            colorType = ColorType.RGBA_8888,
                                            alphaType = ColorAlphaType.PREMUL
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
                    }
                }
            }

            else -> Unit
        }
        onDispose {
            foregroundRendererJob?.cancel()
            foregroundRendererJob = null

            foregroundRenderJob?.cancel()
            foregroundRenderJob = null
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            image != null -> {
                val backgroundSize = remember(
                    background.scale,
                    image.width,
                    image.height,
                    maxWidth,
                    maxHeight
                ) {
                    when (background) {
                        is Background.Blur -> background.scale.scaleDp(
                            DpSize(image.width.dp, image.height.dp),
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
                    image.width,
                    image.height,
                    foreground.scale,
                    maxWidth,
                    maxHeight
                ) {
                    foreground.scale.scaleDp(
                        DpSize(image.width.dp, image.height.dp), DpSize(maxWidth, maxHeight)
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
                                        image,
                                        Rect(
                                            0f,
                                            0f,
                                            image.width.toFloat(),
                                            image.height.toFloat()
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
                                image,
                                Rect(
                                    0f,
                                    0f,
                                    image.width.toFloat(),
                                    image.height.toFloat()
                                ),
                                Rect(
                                    foregroundOffset.x.toPx(),
                                    foregroundOffset.y.toPx(),
                                    foregroundOffset.x.toPx() + foregroundSize.width.toPx(),
                                    foregroundOffset.y.toPx() + foregroundSize.height.toPx(),
                                ),
                                Paint()
                            )
                        }
                    }
                }
            }

            else -> placeholder?.invoke(this)
        }
    }
}