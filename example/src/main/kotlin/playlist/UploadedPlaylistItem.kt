package playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import renderer.Foreground
import renderer.Renderer
import scale.ImageScale

@Composable
fun UploadedPlaylistItem(
    playlistItem: PlaylistItem.Uploaded,
    isSelected: Boolean,
    select: () -> Unit,
    delete: () -> Unit,
) {
    Card(modifier = Modifier.alpha(if (isSelected) .5f else 1f)) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).clickable(onClick = select).padding(8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                horizontalArrangement = Arrangement.spacedBy(
                    space = 8.dp,
                    alignment = Alignment.Start
                ),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Card(shape = CircleShape) {
                    Renderer(
                        modifier = Modifier.fillMaxHeight().aspectRatio(1f),
                        foreground = playlistItem.snapshot?.let { snapshot ->
                            Foreground.Frame(frame = snapshot, scale = ImageScale.Crop)
                        } ?: Foreground.Empty,
                    )
                }
                Text(
                    text = playlistItem.media.location.value,
                    modifier = Modifier.padding(8.dp),
                    maxLines = 1
                )
            }
            IconButton(onClick = delete) {
                Icon(Icons.Default.Remove, null)
            }
        }
    }
}