package snapshot

import decoder.Decoder
import frame.Frame
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

object SnapshotManager {
    suspend fun snapshot(
        location: String,
        timestampMillis: (durationMillis: Long) -> (Long),
    ) = Decoder.createVideoDecoder(location).mapCatching { decoder ->
        with(decoder) {
            use {
                seekTo(micros = timestampMillis(media.durationMicros.microseconds.inWholeMilliseconds)).getOrThrow()

                checkNotNull((nextFrame().getOrThrow() as? Frame.Video.Content)) { "Unable to get snapshot" }
            }
        }
    }

    suspend fun snapshots(
        location: String,
        timestampsMillis: (durationMillis: Long) -> (List<Long>),
    ) = Decoder.createVideoDecoder(location).mapCatching { decoder ->
        with(decoder) {
            use {
                timestampsMillis(media.durationMicros.microseconds.inWholeMilliseconds)
                    .map { timestampMillis -> timestampMillis.milliseconds.inWholeMicroseconds }
                    .map { timestampMicros ->
                        seekTo(micros = timestampMicros).getOrThrow()

                        checkNotNull((nextFrame().getOrThrow() as? Frame.Video.Content)) { "Unable to get snapshot" }
                    }
                    .toList()
            }
        }
    }
}