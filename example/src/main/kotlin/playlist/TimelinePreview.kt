package playlist

import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.github.numq.klarity.compose.renderer.Foreground
import com.github.numq.klarity.compose.renderer.RendererComponent
import com.github.numq.klarity.compose.scale.ImageScale
import com.github.numq.klarity.core.renderer.Renderer
import controls.HoveredTimestamp
import extension.formatTimestamp

@Composable
fun TimelinePreview(
    width: Float,
    height: Float,
    hoveredTimestamp: HoveredTimestamp?,
    previewRenderer: Renderer
) {
    BoxWithConstraints(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.BottomStart) {
        hoveredTimestamp?.run {
            Card(modifier = Modifier.padding(4.dp).graphicsLayer {
                translationX = (offset.x - width.div(2)).coerceIn(0f, maxWidth.value - width)
            }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space = 4.dp, alignment = Alignment.CenterVertically)
                ) {
                    RendererComponent(
                        modifier = Modifier.size(width = width.dp, height = height.dp),
                        foreground = Foreground(renderer = previewRenderer, imageScale = ImageScale.Crop)
                    )
                    Box(modifier = Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                        Text(text = millis.formatTimestamp(), modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}