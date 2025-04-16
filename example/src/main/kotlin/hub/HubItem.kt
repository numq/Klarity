package hub

import com.github.numq.klarity.core.player.KlarityPlayer
import com.github.numq.klarity.core.preview.PreviewManager
import com.github.numq.klarity.core.renderer.Renderer
import java.util.*

sealed class HubItem private constructor(open val id: Long) {
    data class Pending(val location: String) : HubItem(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)

    data class Uploaded(
        val player: KlarityPlayer,
        val previewManager: PreviewManager?,
        val renderer: Renderer?
    ) : HubItem(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)
}