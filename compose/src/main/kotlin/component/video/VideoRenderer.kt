package component.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.*
import decoder.DecodedFrame
import media.Media
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import scale.ImageScale

@Composable
fun VideoRenderer(
    media: Media,
    videoFrame: DecodedFrame.Video?,
    blurredBackground: Boolean = false,
    modifier: Modifier = Modifier,
    onContentOffset: (DpOffset) -> Unit = {},
    onContentSize: (DpSize) -> Unit = {},
    placeholder: @Composable BoxScope.() -> Unit = {
        Icon(Icons.Rounded.Image, "unable to draw pixels", modifier = Modifier.fillMaxSize())
    },
) {

    val bitmap = remember(media) {
        media.size?.let { (imageWidth, imageHeight) ->
            Bitmap().apply {
                allocPixels(
                    ImageInfo(
                        width = imageWidth,
                        height = imageHeight,
                        colorType = ColorType.BGRA_8888,
                        alphaType = ColorAlphaType.PREMUL
                    )
                )
            }
        }
    }

    bitmap?.let {
        LaunchedEffect(videoFrame?.timestampNanos) {
            videoFrame?.bytes?.takeIf(ByteArray::isNotEmpty)?.let(bitmap::installPixels)
        }
    }

    BoxWithConstraints(modifier) {

        val foregroundOffsetSize by remember(media, maxWidth, maxHeight) {
            derivedStateOf {
                media.size?.let { (imageWidth, imageHeight) ->
                    ImageScale.Fit.scaleDp(
                        imageWidth.dp,
                        imageHeight.dp,
                        maxWidth,
                        maxHeight
                    ).also { (offset, size) ->
                        onContentOffset(offset)
                        onContentSize(size)
                    }
                } ?: (DpOffset.Zero to DpSize.Zero)
            }
        }

        val backgroundOffsetSize by remember(media, maxWidth, maxHeight) {
            derivedStateOf {
                media.size?.let { (imageWidth, imageHeight) ->
                    ImageScale.Fill.scaleDp(
                        imageWidth.dp,
                        imageHeight.dp,
                        maxWidth,
                        maxHeight
                    )
                } ?: (DpOffset.Zero to DpSize.Zero)
            }
        }

        if (blurredBackground) Canvas(Modifier.fillMaxSize().blur(32.dp)) {
            bitmap?.run {
                drawImage(
                    asComposeImageBitmap(),
                    dstOffset = IntOffset(
                        backgroundOffsetSize.first.x.roundToPx(),
                        backgroundOffsetSize.first.y.roundToPx()
                    ),
                    dstSize = IntSize(
                        backgroundOffsetSize.second.width.roundToPx(),
                        backgroundOffsetSize.second.height.roundToPx()
                    )
                )
            }
        }

        Canvas(Modifier.background(Color.Transparent).fillMaxSize()) {
            bitmap?.run {
                drawImage(
                    asComposeImageBitmap(),
                    dstOffset = IntOffset(
                        foregroundOffsetSize.first.x.roundToPx(),
                        foregroundOffsetSize.first.y.roundToPx()
                    ),
                    dstSize = IntSize(
                        foregroundOffsetSize.second.width.roundToPx(),
                        foregroundOffsetSize.second.height.roundToPx()
                    )
                )
            }
        }

        if (bitmap == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            placeholder()
        }
    }
}