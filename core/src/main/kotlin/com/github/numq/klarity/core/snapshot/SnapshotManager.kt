package com.github.numq.klarity.core.snapshot

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

object SnapshotManager {
    suspend fun snapshots(
        location: String,
        width: Int? = null,
        height: Int? = null,
        hardwareAccelerationCandidates: List<HardwareAcceleration> = emptyList(),
        keyframesOnly: Boolean = true,
        timestampsMillis: (durationMillis: Long) -> (List<Long>) = { listOf(0L) },
    ) = VideoDecoderFactory().create(
        parameters = VideoDecoderFactory.Parameters(
            location = location,
            width = width,
            height = height,
            frameRate = null,
            hardwareAccelerationCandidates = hardwareAccelerationCandidates
        )
    ).mapCatching { decoder ->
        try {
            timestampsMillis(decoder.media.durationMicros.microseconds.inWholeMilliseconds).filter {
                it in 0L..decoder.media.durationMicros
            }.map { timestampMillis ->
                timestampMillis.milliseconds.inWholeMicroseconds
            }.mapNotNull { timestampMicros ->
                decoder.seekTo(micros = timestampMicros, keyframesOnly = keyframesOnly).getOrNull()

                decoder.decode().getOrNull() as? Frame.Video.Content
            }
        } finally {
            decoder.close().getOrThrow()
        }
    }.recoverCatching { t ->
        throw SnapshotException(t)
    }

    suspend fun snapshot(
        location: String,
        width: Int? = null,
        height: Int? = null,
        hardwareAccelerationCandidates: List<HardwareAcceleration> = emptyList(),
        keyframesOnly: Boolean = true,
        timestampMillis: (durationMillis: Long) -> (Long) = { 0L },
    ) = snapshots(location = location,
        width = width,
        height = height,
        keyframesOnly = keyframesOnly,
        hardwareAccelerationCandidates = hardwareAccelerationCandidates,
        timestampsMillis = { durationMillis ->
            listOf(timestampMillis(durationMillis))
        }).map(List<Frame.Video.Content?>::firstOrNull)
}