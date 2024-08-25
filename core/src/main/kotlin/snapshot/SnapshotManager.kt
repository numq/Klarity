package snapshot

import decoder.Decoder
import frame.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

object SnapshotManager {
    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    suspend fun snapshot(
        location: String,
        keyframesOnly: Boolean = false,
        timestampMillis: (durationMillis: Long) -> (Long),
    ) = withContext(coroutineContext) {
        Decoder.createVideoDecoder(location).mapCatching { decoder ->
            with(decoder) {
                use {
                    seekTo(
                        micros = timestampMillis(media.durationMicros.microseconds.inWholeMilliseconds),
                        keyframesOnly = keyframesOnly
                    ).getOrThrow()

                    nextFrame().getOrThrow() as? Frame.Video.Content
                }
            }
        }
    }

    suspend fun snapshots(
        location: String,
        keyframesOnly: Boolean = false,
        timestampsMillis: (durationMillis: Long) -> (List<Long>),
    ) = withContext(coroutineContext) {
        Decoder.createVideoDecoder(location).mapCatching { decoder ->
            with(decoder) {
                use {
                    timestampsMillis(media.durationMicros.microseconds.inWholeMilliseconds)
                        .map { timestampMillis -> timestampMillis.milliseconds.inWholeMicroseconds }
                        .mapNotNull { timestampMicros ->
                            seekTo(micros = timestampMicros, keyframesOnly = keyframesOnly).getOrThrow()

                            nextFrame().getOrThrow() as? Frame.Video.Content
                        }.toList()
                }
            }
        }
    }
}