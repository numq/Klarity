package component.video

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.BrokenImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.unit.*
import media.Media
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import scale.ImageScale

@Composable
fun VideoRenderer(
    media: Media,
    pixels: ByteArray?,
    modifier: Modifier = Modifier,
    onContentOffset: (DpOffset) -> Unit = {},
    onContentSize: (DpSize) -> Unit = {},
    placeholder: @Composable BoxScope.() -> Unit = {
        Icon(Icons.Rounded.BrokenImage, "unable to draw pixels")
    },
) {
    val bitmap = remember(media) {
        media.takeIf { it.width > 0 && it.height > 0 }?.let {
            Bitmap().apply {
                allocPixels(
                    ImageInfo(
                        width = it.width,
                        height = it.height,
                        colorType = ColorType.BGRA_8888,
                        alphaType = ColorAlphaType.OPAQUE
                    )
                )
            }
        }
    }

    LaunchedEffect(pixels.contentHashCode()) {
        if (pixels?.isNotEmpty() == true) bitmap?.installPixels(pixels)
    }

    BoxWithConstraints(modifier) {

        val offsetSize by remember(media, maxWidth, maxHeight) {
            derivedStateOf {
                ImageScale.Fit.scaleDp(
                    media.width.dp,
                    media.height.dp,
                    maxWidth,
                    maxHeight
                ).also { (offset, size) ->
                    onContentOffset(offset)
                    onContentSize(size)
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            bitmap?.run {
                drawImage(
                    asComposeImageBitmap(),
                    dstOffset = IntOffset(offsetSize.first.x.roundToPx(), offsetSize.first.y.roundToPx()),
                    dstSize = IntSize(offsetSize.second.width.roundToPx(), offsetSize.second.height.roundToPx())
                )
            }
        }

        if (bitmap == null) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            placeholder()
        }
    }
}