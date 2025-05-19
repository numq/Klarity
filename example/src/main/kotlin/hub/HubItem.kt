package hub

import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.player.KlarityPlayer
import io.github.numq.klarity.renderer.Renderer
import java.util.*

sealed class HubItem private constructor(open val id: Long) {
    data class Pending(val location: String) : HubItem(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)

    data class Uploaded(
        val player: KlarityPlayer,
        val renderer: Renderer?,
        val snapshots: List<Frame.Content.Video>
    ) : HubItem(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE) {
        suspend fun close() {
            player.close().getOrThrow()

            renderer?.close()?.getOrThrow()

            snapshots.forEach(Frame.Content.Video::close)
        }
    }
}