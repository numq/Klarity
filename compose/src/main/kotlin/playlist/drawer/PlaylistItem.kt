package playlist.drawer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asComposeImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import media.Media
import org.jetbrains.skia.Bitmap
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import playlist.PlaylistMedia

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LazyItemScope.PlaylistItem(
    playlistMedia: PlaylistMedia,
    play: () -> Unit,
    remove: () -> Unit,
    modifier: Modifier = Modifier,
) {

    val skiaBitmap by rememberSaveable(playlistMedia.media.info) {
        derivedStateOf {
            playlistMedia.media.info.size?.let { (imageWidth, imageHeight) ->
                Bitmap().apply {
                    allocPixels(
                        ImageInfo(
                            width = imageWidth,
                            height = imageHeight,
                            colorType = ColorType.BGRA_8888,
                            alphaType = ColorAlphaType.PREMUL
                        )
                    )
                    playlistMedia.media.info.previewFrame?.bytes
                        ?.takeIf { it.size == width * height * 4 }
                        ?.let(::installPixels)
                }
            }
        }
    }

    val (isRemovalPending, setIsRemovalPending) = rememberSaveable { mutableStateOf(false) }

    val slideAnimationSpec = rememberSaveable<AnimationSpec<Float>> {
        tween(durationMillis = 250, easing = LinearEasing)
    }

    BoxWithConstraints(contentAlignment = Alignment.Center) {

        val offsetAnimatable = rememberSaveable { Animatable(if (isRemovalPending) 0f else -maxWidth.value) }

        LaunchedEffect(isRemovalPending) {
            offsetAnimatable.animateTo(
                targetValue = if (isRemovalPending) -maxWidth.value else 0f,
                animationSpec = slideAnimationSpec
            )
            if (isRemovalPending) remove()
        }

        Card(modifier = modifier.graphicsLayer(translationX = offsetAnimatable.value).animateItemPlacement()) {
            Row(
                modifier = Modifier.fillMaxWidth().clickable { play() }.padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                skiaBitmap?.run {
                    Image(
                        asComposeImageBitmap(),
                        "preview",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(64.dp).clip(CircleShape)
                    )
                } ?: Icon(Icons.Rounded.Image, "preview", modifier = Modifier.size(64.dp))
                val title = when (val media = playlistMedia.media) {
                    is Media.Local -> media.name
                    is Media.Remote -> media.url
                }
                Text(title, modifier = Modifier.weight(1f))
                IconButton(onClick = { setIsRemovalPending(true) }) {
                    Icon(Icons.Rounded.Remove, "remove media from playlist")
                }
            }
        }
    }
}