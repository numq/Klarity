package snapshot

import decoder.Decoder
import frame.Frame
import kotlin.time.Duration.Companion.milliseconds

object SnapshotManager {
    suspend fun snapshot(
        location: String,
        millis: Long,
    ) = Decoder.createVideoDecoder(location).mapCatching { decoder ->
        with(decoder) {
            use {
                if (millis > 0) seekTo(micros = millis.milliseconds.inWholeMicroseconds).getOrThrow()
                checkNotNull(nextFrame().getOrThrow() as? Frame.Video.Content) { "Unable to get snapshot" }
            }
        }
    }
}