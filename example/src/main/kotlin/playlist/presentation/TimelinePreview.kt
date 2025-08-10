package playlist.presentation

import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.renderer.compose.Foreground
import io.github.numq.klarity.renderer.compose.ImageScale
import io.github.numq.klarity.renderer.compose.RendererComponent
import timestamp.formatTimestamp

@Composable
fun TimelinePreview(
    width: Float,
    height: Float,
    bottomPadding: Float,
    previewTimestamp: PreviewTimestamp,
    renderer: Renderer,
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        Card(modifier = Modifier.padding(4.dp).graphicsLayer {
            translationX = (previewTimestamp.offset.x - width.div(2)).coerceIn(0f, maxWidth.value - width)
            translationY = -bottomPadding
        }) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(space = 4.dp, alignment = Alignment.CenterVertically)
            ) {
                RendererComponent(
                    modifier = Modifier.size(width = width.dp, height = height.dp),
                    foreground = Foreground(renderer = renderer, imageScale = ImageScale.Crop)
                )
                Box(modifier = Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                    Text(
                        text = previewTimestamp.timestamp.inWholeMilliseconds.formatTimestamp(),
                        modifier = Modifier.padding(4.dp)
                    )
                }
            }
        }
    }
}