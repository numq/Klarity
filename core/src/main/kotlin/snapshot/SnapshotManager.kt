package snapshot

import decoder.Decoder
import frame.Frame
import kotlin.time.Duration.Companion.milliseconds

object SnapshotManager {
    suspend fun snapshot(
        location: String,
        timestampMillis: Long,
    ) = Decoder.createVideoDecoder(location).mapCatching { decoder ->
        with(decoder) {
            use {
                if (timestampMillis > 0) seekTo(micros = timestampMillis.milliseconds.inWholeMicroseconds).getOrThrow()
                checkNotNull(nextFrame().getOrThrow() as? Frame.Video.Content) { "Unable to get snapshot" }
            }
        }
    }

    suspend fun snapshots(
        location: String,
        timestampsMillis: List<Long>,
    ) = Decoder.createVideoDecoder(location).mapCatching { decoder ->
        with(decoder) {
            use {
                buildList {
                    for (timestampMillis in timestampsMillis) {
                        if (timestampMillis > 0) seekTo(micros = timestampMillis.milliseconds.inWholeMicroseconds).getOrThrow()
                        checkNotNull((nextFrame().getOrThrow() as? Frame.Video.Content)?.also(::add)) { "Unable to get snapshot" }
                    }
                }
            }
        }
    }
}