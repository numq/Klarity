package hub

import io.github.numq.klarity.player.KlarityPlayer
import io.github.numq.klarity.renderer.SkiaRenderer
import java.util.*

sealed class HubItem private constructor(open val id: Long) {
    data class Pending(val location: String) : HubItem(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)

    data class Uploaded(
        val player: KlarityPlayer,
        val renderer: SkiaRenderer?
    ) : HubItem(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)
}