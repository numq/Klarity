package hub

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.player.KlarityPlayer
import java.util.*

sealed class HubItem private constructor(open val id: Long) {
    data class Pending(val location: String) : HubItem(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)

    data class Uploaded(
        val player: KlarityPlayer,
        val snapshots: List<Frame.Video.Content>,
    ) : HubItem(UUID.randomUUID().mostSignificantBits and Long.MAX_VALUE)
}