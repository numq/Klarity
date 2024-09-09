package hub

import frame.Frame
import media.Media

sealed interface HubItem {
    data class Pending(val id: Long, val location: String) : HubItem

    data class Uploaded(val media: Media, val snapshots: List<Frame.Video.Content>) : HubItem
}