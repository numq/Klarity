package io.github.numq.example.playlist.presentation

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.TooltipArea
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AudioFile
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.numq.example.item.Item
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.renderer.compose.Foreground
import io.github.numq.klarity.renderer.compose.ImageScale
import io.github.numq.klarity.renderer.compose.RendererComponent

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LoadedPlaylistItem(
    item: Item,
    renderer: Renderer?,
    isSelected: Boolean,
    select: (Item) -> Unit,
    remove: (Item) -> Unit,
) {
    var isTooltipVisible by remember(item.id, item.location) { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth().height(64.dp).alpha(if (isSelected) .5f else 1f)) {
        Row(
            modifier = Modifier.fillMaxWidth().clickable(onClick = { select(item) }).padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(
                space = 4.dp, alignment = Alignment.Start
            ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier.aspectRatio(1f).clip(CircleShape),
                contentAlignment = Alignment.Center
            ) {
                when {
                    renderer == null -> Icon(
                        Icons.Default.AudioFile,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    renderer.drawsNothing() -> Icon(
                        Icons.Default.BrokenImage,
                        null,
                        tint = MaterialTheme.colorScheme.primary
                    )

                    else -> RendererComponent(
                        modifier = Modifier.fillMaxSize(),
                        foreground = Foreground(renderer = renderer, imageScale = ImageScale.Crop)
                    )
                }
            }
            TooltipArea(tooltip = {
                if (isTooltipVisible) {
                    Card {
                        Box(modifier = Modifier.fillMaxWidth(.5f).padding(8.dp), contentAlignment = Alignment.Center) {
                            Text(text = item.location, color = MaterialTheme.colorScheme.primary)
                        }
                    }
                }
            }, content = {
                Text(
                    text = item.location,
                    maxLines = 1,
                    color = MaterialTheme.colorScheme.primary,
                    onTextLayout = { textLayoutResult ->
                        isTooltipVisible = textLayoutResult.hasVisualOverflow
                    }
                )
            }, modifier = Modifier.weight(1f).padding(horizontal = 4.dp))
            IconButton(onClick = { remove(item) }) {
                Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}