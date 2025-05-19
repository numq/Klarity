package playlist

import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.renderer.Renderer
import java.util.*

sealed class PlaylistItem private constructor(open val id: Long) {
    data class Pending(val location: String) : PlaylistItem(
        UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    )

    data class Uploaded(val media: Media, val renderer: Renderer?, val preview: Frame.Content.Video?) : PlaylistItem(
        UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE
    ) {
        suspend fun close() {
            renderer?.close()?.getOrThrow()

            preview?.close()
        }
    }
}