package com.github.numq.klarity.core.snapshot

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.frame.Frame
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

object SnapshotManager {
    suspend fun snapshot(
        location: String,
        width: Int? = null,
        height: Int? = null,
        keyframesOnly: Boolean = false,
        timestampMillis: (durationMillis: Long) -> (Long),
    ) = VideoDecoderFactory().create(
        parameters = VideoDecoderFactory.Parameters(location = location)
    ).mapCatching { decoder ->
        with(decoder) {
            use {
                seekTo(
                    micros = timestampMillis(media.durationMicros.microseconds.inWholeMilliseconds),
                    keyframesOnly = keyframesOnly
                ).getOrThrow()

                nextFrame(width = width, height = height).getOrThrow() as? Frame.Video.Content
            }
        }
    }.recoverCatching { t ->
        throw Exception("Snapshot is only available for media containing video: ${t.localizedMessage}")
    }

    suspend fun snapshots(
        location: String,
        width: Int? = null,
        height: Int? = null,
        keyframesOnly: Boolean = false,
        timestampsMillis: (durationMillis: Long) -> (List<Long>),
    ) = VideoDecoderFactory().create(
        parameters = VideoDecoderFactory.Parameters(location = location)
    ).mapCatching { decoder ->
        with(decoder) {
            use {
                timestampsMillis(media.durationMicros.microseconds.inWholeMilliseconds)
                    .map { timestampMillis -> timestampMillis.milliseconds.inWholeMicroseconds }
                    .mapNotNull { timestampMicros ->
                        seekTo(micros = timestampMicros, keyframesOnly = keyframesOnly).getOrThrow()

                        nextFrame(width = width, height = height).getOrThrow() as? Frame.Video.Content
                    }.toList()
            }
        }
    }.recoverCatching { t ->
        throw Exception("Snapshots are only available for media containing video: ${t.localizedMessage}")
    }
}