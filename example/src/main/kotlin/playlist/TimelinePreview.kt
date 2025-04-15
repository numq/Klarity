package playlist

import androidx.compose.foundation.layout.*
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.github.numq.klarity.compose.renderer.Foreground
import com.github.numq.klarity.compose.renderer.RendererComponent
import com.github.numq.klarity.compose.scale.ImageScale
import com.github.numq.klarity.core.preview.PreviewManager
import controls.HoveredTimestamp
import extension.formatTimestamp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest

@Composable
fun TimelinePreview(
    width: Float,
    height: Float,
    hoveredTimestamps: SharedFlow<HoveredTimestamp?>,
    previewManager: PreviewManager,
) {
    var previewJob by remember { mutableStateOf<Job?>(null) }

    var previewItem by remember { mutableStateOf<TimelinePreviewItem?>(null) }

    LaunchedEffect(hoveredTimestamps) {
        hoveredTimestamps.collectLatest { hoveredTimestamp ->
            previewItem = hoveredTimestamp?.run {
                previewManager.preview(millis).getOrThrow()

                TimelinePreviewItem(offset = offset, millis = millis)
            }
        }
    }

    DisposableEffect(hoveredTimestamps) {
        onDispose {
            previewJob?.cancel()
            previewJob = null
        }
    }

    BoxWithConstraints(contentAlignment = Alignment.BottomStart) {
        previewItem?.run {
            Card(modifier = Modifier.padding(4.dp).graphicsLayer {
                translationX = (offset.x - width.div(2)).coerceIn(0f, maxWidth.value - width)
            }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space = 4.dp, alignment = Alignment.CenterVertically)
                ) {
                    RendererComponent(
                        modifier = Modifier.size(width = width.dp, height = height.dp),
                        foreground = Foreground.Source(
                            renderer = previewManager.renderer,
                            imageScale = ImageScale.Crop
                        ),
                        placeholder = {
                            Icon(Icons.Default.BrokenImage, null)
                        }
                    )
                    Box(modifier = Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                        Text(text = millis.formatTimestamp(), modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}