package com.github.numq.klarity.core.snapshot

import com.github.numq.klarity.core.closeable.use
import com.github.numq.klarity.core.decoder.HardwareAcceleration
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.frame.Frame
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

object SnapshotManager {
    suspend fun snapshot(
        location: String,
        width: Int? = null,
        height: Int? = null,
        keyframesOnly: Boolean = true,
        hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.NONE,
        timestampMillis: (durationMillis: Long) -> (Long) = { 0L },
    ) = VideoDecoderFactory().create(
        parameters = VideoDecoderFactory.Parameters(location = location, hardwareAcceleration = hardwareAcceleration)
    ).mapCatching { decoder ->
        with(decoder) {
            use {
                timestampMillis(media.durationMicros.microseconds.inWholeMilliseconds).takeIf {
                    it > 0
                }?.let { timestampMicros ->
                    seekTo(micros = timestampMicros, keyframesOnly = keyframesOnly).getOrThrow()
                }

                nextFrame(width = width, height = height).getOrThrow() as? Frame.Video.Content
            }
        }
    }

    suspend fun snapshots(
        location: String,
        width: Int? = null,
        height: Int? = null,
        keyframesOnly: Boolean = true,
        hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.NONE,
        timestampsMillis: (durationMillis: Long) -> (List<Long>) = { listOf(0L) },
    ) = VideoDecoderFactory().create(
        parameters = VideoDecoderFactory.Parameters(location = location, hardwareAcceleration = hardwareAcceleration)
    ).mapCatching { decoder ->
        with(decoder) {
            use {
                timestampsMillis(media.durationMicros.microseconds.inWholeMilliseconds)
                    .map { timestampMillis -> timestampMillis.milliseconds.inWholeMicroseconds }
                    .mapNotNull { timestampMicros ->
                        seekTo(micros = timestampMicros, keyframesOnly = keyframesOnly).getOrThrow()

                        nextFrame(width = width, height = height).getOrThrow() as? Frame.Video.Content
                    }
            }
        }
    }
}