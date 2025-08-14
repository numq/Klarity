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
import androidx.compose.runtime.Composable
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
    Row(
        modifier = Modifier.fillMaxWidth().height(64.dp),
        horizontalArrangement = Arrangement.spacedBy(space = 4.dp, alignment = Alignment.Start),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TooltipArea(tooltip = {
            Card(modifier = Modifier.padding(8.dp)) {
                Text(text = item.location, color = MaterialTheme.colorScheme.primary)
            }
        }, modifier = Modifier.weight(1f), content = {
            Card(modifier = Modifier.fillMaxSize().alpha(if (isSelected) .5f else 1f)) {
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

                    Text(
                        text = item.location,
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp),
                        maxLines = 1,
                        softWrap = false,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        })

        IconButton(onClick = { remove(item) }) {
            Icon(Icons.Default.Remove, null, tint = MaterialTheme.colorScheme.primary)
        }
    }
}