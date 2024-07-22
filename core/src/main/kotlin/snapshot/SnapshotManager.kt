package snapshot

import decoder.Decoder
import frame.Frame
import media.Media
import probe.ProbeManager
import kotlin.time.Duration.Companion.milliseconds

object SnapshotManager {
    suspend fun snapshot(
        location: String,
        timestampMillis: (Media) -> (Long),
    ) = ProbeManager.probe(location = location).mapCatching { media ->
        checkNotNull(media.videoFormat)
        Decoder.createVideoDecoder(location).mapCatching { decoder ->
            with(decoder) {
                use {
                    timestampMillis(media).takeIf { it > 0 }?.milliseconds?.inWholeMicroseconds?.let { timestampMicros ->
                        seekTo(micros = timestampMicros).getOrThrow()
                        checkNotNull(nextFrame().getOrThrow() as? Frame.Video.Content) { "Unable to get snapshot" }
                    }
                }
            }
        }.getOrThrow()
    }.getOrNull()

    suspend fun snapshots(
        location: String,
        timestampsMillis: (Media) -> (List<Long>),
    ) = ProbeManager.probe(location = location).mapCatching { media ->
        checkNotNull(media.videoFormat)
        Decoder.createVideoDecoder(location).mapCatching { decoder ->
            with(decoder) {
                use {
                    timestampsMillis(media).filter { it > 0 }.map { timestampMillis ->
                        seekTo(micros = timestampMillis.milliseconds.inWholeMicroseconds).getOrThrow()
                        checkNotNull((nextFrame().getOrThrow() as? Frame.Video.Content)) { "Unable to get snapshot" }
                    }
                }
            }
        }.getOrThrow()
    }.getOrNull()
}