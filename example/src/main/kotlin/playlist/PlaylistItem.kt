package playlist

import io.github.numq.klarity.media.Media
import io.github.numq.klarity.renderer.SkiaRenderer
import java.util.*

sealed class PlaylistItem private constructor(open val id: Long) {
    data class Pending(val location: String) : PlaylistItem(
        UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    )

    data class Uploaded(val media: Media, val renderer: SkiaRenderer?) : PlaylistItem(
        UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    )
}