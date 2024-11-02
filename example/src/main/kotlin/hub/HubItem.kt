package hub

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media

sealed interface HubItem {
    data class Pending(val id: Long, val location: String) : HubItem

    data class Uploaded(val media: Media, val snapshots: List<Frame.Video.Content>) : HubItem
}