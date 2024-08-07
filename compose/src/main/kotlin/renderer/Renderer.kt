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
import frame.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.filterIsInstance
import kotlinx.coroutines.flow.filterNotNull
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

    val (previewFrameImage, setPreviewFrameImage) = remember { mutableStateOf<Image?>(null) }

    val (playingFrameImage, setPlayingFrameImage) = remember { mutableStateOf<Image?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            if (!backgroundPaint.isClosed) backgroundPaint.close()
            if (previewFrameImage?.isClosed == false) previewFrameImage.close()
            if (playingFrameImage?.isClosed == false) playingFrameImage.close()
        }
    }

    LaunchedEffect(foreground) {
        withContext(Dispatchers.Default) {
            /*when (foreground) {
                is Foreground.Empty -> Unit

                is Foreground.Source -> with(foreground) {
                    renderer.preview?.run {
                        val imageInfo = ImageInfo(
                            width = width,
                            height = height,
                            colorType = ColorType.RGB_565,
                            alphaType = ColorAlphaType.OPAQUE
                        )

                        if (previewFrameImage?.isClosed == false) previewFrameImage.close()

                        setPreviewFrameImage(
                            Image.makeRaster(
                                imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel
                            )
                        )
                    }
                    renderer.frame.filterNotNull().filterIsInstance<Frame.Video.Content>().collectLatest { frame ->
                        val timeTaken = measureTimeMillis {
                            with(frame) {
                                val imageInfo = ImageInfo(
                                    width = width,
                                    height = height,
                                    colorType = ColorType.RGB_565,
                                    alphaType = ColorAlphaType.OPAQUE
                                )

                                if (playingFrameImage?.isClosed == false) playingFrameImage.close()

                                setPlayingFrameImage(
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

                    if (image?.isClosed == false) image.close()

                    setImage(
                        Image.makeRaster(
                            imageInfo, frame.bytes, imageInfo.width * imageInfo.bytesPerPixel
                        )
                    )
                }

                is Foreground.Cover -> with(foreground) {
                    val imageInfo = ImageInfo(
                        width = width, height = height, colorType = colorType, alphaType = alphaType
                    )

                    if (image?.isClosed == false) image.close()

                    setImage(
                        Image.makeRaster(
                            imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel
                        )
                    )
                }
            }*/
            when (foreground) {
                is Foreground.Source -> with(foreground) {
                    renderer.preview?.run {
                        val imageInfo = ImageInfo(
                            width = width,
                            height = height,
                            colorType = ColorType.RGB_565,
                            alphaType = ColorAlphaType.OPAQUE
                        )

                        if (previewFrameImage?.isClosed == false) previewFrameImage.close()

                        setPreviewFrameImage(
                            Image.makeRaster(
                                imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel
                            )
                        )
                    }
                    renderer.frame.filterNotNull().filterIsInstance<Frame.Video.Content>().collectLatest { frame ->
                        val timeTaken = measureTimeMillis {
                            with(frame) {
                                val imageInfo = ImageInfo(
                                    width = width,
                                    height = height,
                                    colorType = ColorType.RGB_565,
                                    alphaType = ColorAlphaType.OPAQUE
                                )

                                if (playingFrameImage?.isClosed == false) playingFrameImage.close()

                                setPlayingFrameImage(
                                    Image.makeRaster(
                                        imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel
                                    )
                                )
                            }
                        }
                        if (logRenderingTime) println("Rendering time: $timeTaken ms")
                    }
                }

                else -> Unit
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        when {
            playingFrameImage != null -> {
                val backgroundSize = remember(
                    background.scale,
                    playingFrameImage.width,
                    playingFrameImage.height,
                    maxWidth,
                    maxHeight
                ) {
                    when (background) {
                        is Background.Blur -> background.scale.scaleDp(
                            DpSize(playingFrameImage.width.dp, playingFrameImage.height.dp),
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
                    playingFrameImage.width,
                    playingFrameImage.height,
                    foreground.scale,
                    maxWidth,
                    maxHeight
                ) {
                    foreground.scale.scaleDp(
                        DpSize(playingFrameImage.width.dp, playingFrameImage.height.dp), DpSize(maxWidth, maxHeight)
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
                                        playingFrameImage,
                                        Rect(
                                            0f,
                                            0f,
                                            playingFrameImage.width.toFloat(),
                                            playingFrameImage.height.toFloat()
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
                                playingFrameImage,
                                Rect(
                                    0f,
                                    0f,
                                    playingFrameImage.width.toFloat(),
                                    playingFrameImage.height.toFloat()
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

            previewFrameImage != null -> {
                val backgroundSize = remember(
                    background.scale,
                    previewFrameImage.width,
                    previewFrameImage.height,
                    maxWidth,
                    maxHeight
                ) {
                    when (background) {
                        is Background.Blur -> background.scale.scaleDp(
                            DpSize(previewFrameImage.width.dp, previewFrameImage.height.dp),
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
                    previewFrameImage.width,
                    previewFrameImage.height,
                    foreground.scale,
                    maxWidth,
                    maxHeight
                ) {
                    foreground.scale.scaleDp(
                        DpSize(previewFrameImage.width.dp, previewFrameImage.height.dp), DpSize(maxWidth, maxHeight)
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
                                        previewFrameImage,
                                        Rect(
                                            0f,
                                            0f,
                                            previewFrameImage.width.toFloat(),
                                            previewFrameImage.height.toFloat()
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
                                previewFrameImage,
                                Rect(
                                    0f,
                                    0f,
                                    previewFrameImage.width.toFloat(),
                                    previewFrameImage.height.toFloat()
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