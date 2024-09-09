package playlist

import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Remove
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun PendingPlaylistItem(
    playlistItem: PlaylistItem.Pending,
    delete: () -> Unit,
) {
    val infiniteTransition = rememberInfiniteTransition()

    val backgroundColor by infiniteTransition.animateColor(
        initialValue = Color.LightGray,
        targetValue = Color.Gray,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        )
    )

    Card(backgroundColor = backgroundColor) {
        Row(
            modifier = Modifier.fillMaxWidth().height(64.dp).padding(8.dp),
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
                    Box(modifier = Modifier.fillMaxHeight().aspectRatio(1f))
                }
                Text(
                    text = playlistItem.location,
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