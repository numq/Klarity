package playlist

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.renderer.compose.Foreground
import io.github.numq.klarity.renderer.compose.ImageScale
import io.github.numq.klarity.renderer.compose.RendererComponent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun UploadedPlaylistItem(
    playlistItem: PlaylistItem.Uploaded,
    isSelected: Boolean,
    select: () -> Unit,
    delete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp, alignment = Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TooltipArea(tooltip = {
            Text(text = playlistItem.media.location)
        }, modifier = Modifier.weight(1f), content = {
            Card(modifier = Modifier.fillMaxSize().alpha(if (isSelected) .5f else 1f)) {
                Row(
                    modifier = Modifier.fillMaxWidth().clickable(onClick = select),
                    horizontalArrangement = Arrangement.spacedBy(
                        space = 4.dp, alignment = Alignment.Start
                    ),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    when (playlistItem.media) {
                        is Media.Audio -> Icon(Icons.Default.AudioFile, null)

                        else -> playlistItem.renderer?.let {
                            RendererComponent(
                                modifier = Modifier.aspectRatio(1f).clip(CircleShape),
                                foreground = Foreground(renderer = it, imageScale = ImageScale.Crop),
                            )
                        } ?: Icon(Icons.Default.BrokenImage, null)
                    }
                    Text(
                        text = playlistItem.media.location,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        maxLines = 1,
                        softWrap = false,
                    )
                }
            }
        })
        IconButton(onClick = delete) {
            Icon(Icons.Default.Remove, null)
        }
    }
}