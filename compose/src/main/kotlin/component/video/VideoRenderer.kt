package component.video

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.layout.ContentScale
import media.Media
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo

@Composable
fun VideoRenderer(
    media: Media,
    pixels: ByteArray?,
    modifier: Modifier = Modifier,
    placeholder: @Composable () -> Unit = {},
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

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        bitmap?.run {
            Image(
                bitmap = asComposeImageBitmap(),
                contentDescription = "video frame",
                modifier = Modifier.fillMaxSize(),
                contentScale = if (maxWidth > maxHeight) ContentScale.FillHeight else ContentScale.FillWidth
            )
        } ?: placeholder()
    }
}