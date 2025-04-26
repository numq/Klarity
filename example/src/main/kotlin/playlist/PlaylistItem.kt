package playlist

import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.renderer.Renderer
import java.util.*

sealed class PlaylistItem private constructor(open val id: Long) {
    data class Pending(val location: String) : PlaylistItem(
        UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    )

    data class Uploaded(val media: Media, val renderer: Renderer?) : PlaylistItem(
        UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    )
}