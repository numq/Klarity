package playlist

import androidx.compose.ui.geometry.Offset
import frame.Frame


data class TimelinePreviewItem(val offset: Offset, val millis: Long, val frame: Frame.Video.Content)