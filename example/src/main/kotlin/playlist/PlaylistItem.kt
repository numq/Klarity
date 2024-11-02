package playlist

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media

sealed interface PlaylistItem {
    data class Pending(val id: Long, val location: String) : PlaylistItem

    data class Uploaded(val media: Media, val snapshot: Frame.Video.Content?) : PlaylistItem
}