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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.github.numq.klarity.compose.renderer.Foreground
import com.github.numq.klarity.compose.renderer.RendererComponent
import com.github.numq.klarity.compose.scale.ImageScale
import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import controls.HoveredTimestamp
import extension.formatTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@Composable
fun TimelinePreview(
    width: Float,
    height: Float,
    format: VideoFormat,
    hoveredTimestamps: SharedFlow<HoveredTimestamp?>,
    preview: suspend (timestampMillis: Long) -> Frame.Video.Content?,
) {
    var previewJob by remember { mutableStateOf<Job?>(null) }

    var timelinePreview by remember { mutableStateOf<TimelinePreviewItem?>(null) }

    LaunchedEffect(hoveredTimestamps, format) {
        hoveredTimestamps.collectLatest { hoveredTimestamp ->
            previewJob?.cancelAndJoin()
            previewJob = launch(Dispatchers.Default) {
                timelinePreview = hoveredTimestamp?.let {
                    preview(hoveredTimestamp.millis)?.let { frame ->
                        TimelinePreviewItem(
                            offset = Offset(hoveredTimestamp.offset.x, 0f),
                            millis = hoveredTimestamp.millis,
                            frame = frame
                        )
                    }
                }
            }
        }
    }

    DisposableEffect(hoveredTimestamps, format) {
        onDispose {
            previewJob?.cancel()
            previewJob = null
        }
    }

    BoxWithConstraints(contentAlignment = Alignment.BottomStart) {
        timelinePreview?.let { preview ->
            Card(modifier = Modifier.padding(4.dp).graphicsLayer {
                translationX = (preview.offset.x - width.div(2)).coerceIn(0f, maxWidth.value - width)
            }) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(space = 4.dp, alignment = Alignment.CenterVertically)
                ) {
                    RendererComponent(modifier = Modifier.size(width = width.dp, height = height.dp),
                        foreground = Foreground.Frame(frame = preview.frame, scale = ImageScale.Crop),
                        placeholder = { Icon(Icons.Default.BrokenImage, null) })
                    Box(modifier = Modifier.weight(1f, fill = false), contentAlignment = Alignment.Center) {
                        Text(text = preview.millis.formatTimestamp(), modifier = Modifier.padding(4.dp))
                    }
                }
            }
        }
    }
}