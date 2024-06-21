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
    placeholder: @Composable BoxWithConstraintsScope.() -> Unit = {},
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

    val (image, setImage) = remember { mutableStateOf<Image?>(null) }

    DisposableEffect(Unit) {
        onDispose {
            if (!backgroundPaint.isClosed) backgroundPaint.close()
            if (image?.isClosed == false) image.close()
        }
    }

    LaunchedEffect(foreground) {
        withContext(Dispatchers.Default) {
            when (foreground) {
                is Foreground.Source -> with(foreground) {
                    renderer.frame.filterNotNull().filterIsInstance<Frame.Video.Content>().collectLatest { frame ->
                        val timeTaken = measureTimeMillis {
                            with(frame) {
                                val imageInfo = ImageInfo(
                                    width = width,
                                    height = height,
                                    colorType = ColorType.RGB_565,
                                    alphaType = ColorAlphaType.OPAQUE
                                )

                                if (image?.isClosed == false) image.close()

                                setImage(
                                    Image.makeRaster(
                                        imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel
                                    )
                                )
                            }
                        }
                        println("Rendering time: $timeTaken ms")
                    }
                }

                is Foreground.Cover -> with(foreground) {
                    val imageInfo = ImageInfo(
                        width = width,
                        height = height,
                        colorType = ColorType.BGRA_8888,
                        alphaType = ColorAlphaType.PREMUL
                    )

                    if (image?.isClosed == false) image.close()

                    setImage(
                        Image.makeRaster(
                            imageInfo, bytes, imageInfo.width * imageInfo.bytesPerPixel
                        )
                    )
                }
            }
        }
    }

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        if (image != null) {
            val backgroundSize = remember(background.scale, image.width, image.height, maxWidth, maxHeight) {
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

            val foregroundSize = remember(image.width, image.height, foreground.scale, maxWidth, maxHeight) {
                foreground.scale.scaleDp(
                    DpSize(image.width.dp, image.height.dp),
                    DpSize(maxWidth, maxHeight)
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
                                    Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()),
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
                            Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()),
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
        } else placeholder()
    }
}