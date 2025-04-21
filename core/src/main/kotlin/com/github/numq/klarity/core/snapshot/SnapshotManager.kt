package com.github.numq.klarity.core.snapshot

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import kotlin.time.Duration

/**
 * Provides frame capture functionality for video media at specified timestamps.
 */
object SnapshotManager {
    /**
     * Captures multiple video frames at specified timestamps.
     *
     * @param location Media file path or URI
     * @param width Optional target width (keeps original if null)
     * @param height Optional target height (keeps original if null)
     * @param hardwareAccelerationCandidates Preferred hardware acceleration methods in order of priority
     * @param keyframesOnly If true, seeks only to keyframes (faster but less precise)
     * @param timestamps A function that provides a media duration that can be used to construct desired timestamps
     * @return [Result] containing list of captured frames, or failure if decoding failed
     * @throws SnapshotManagerException if frame capture fails
     */
    suspend fun snapshots(
        location: String,
        width: Int? = null,
        height: Int? = null,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        keyframesOnly: Boolean = true,
        timestamps: (duration: Duration) -> (List<Duration>) = { listOf(Duration.ZERO) },
    ) = runCatching {
        VideoDecoderFactory().create(
            parameters = VideoDecoderFactory.Parameters(
                location = location,
                width = width,
                height = height,
                frameRate = null,
                hardwareAccelerationCandidates = hardwareAccelerationCandidates
            )
        ).mapCatching { decoder ->
            try {
                timestamps(decoder.media.duration).filter {
                    it in Duration.ZERO..decoder.media.duration
                }.mapNotNull { timestamp ->
                    decoder.seekTo(timestamp = timestamp, keyframesOnly = keyframesOnly).getOrNull()

                    decoder.decode().getOrNull() as? Frame.Video.Content
                }
            } finally {
                decoder.close().getOrThrow()
            }
        }.recoverCatching { t ->
            throw SnapshotManagerException(t)
        }.getOrThrow()
    }

    /**
     * Captures a single video frame at specified timestamp.
     *
     * @param location Media file path or URI
     * @param width Optional target width (maintains aspect ratio if null)
     * @param height Optional target height (maintains aspect ratio if null)
     * @param hardwareAccelerationCandidates Preferred hardware acceleration methods in order of priority
     * @param keyframesOnly If true, seeks only to keyframes (faster but less precise)
     * @param timestamp A function that provides a media duration that can be used to construct desired timestamp
     * @return [Result] containing captured frame (nullable if no frame at timestamp), or failure
     * @throws SnapshotManagerException if frame capture fails
     */
    suspend fun snapshot(
        location: String,
        width: Int? = null,
        height: Int? = null,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        keyframesOnly: Boolean = true,
        timestamp: (duration: Duration) -> (Duration) = { Duration.ZERO },
    ) = snapshots(
        location = location,
        width = width,
        height = height,
        keyframesOnly = keyframesOnly,
        hardwareAccelerationCandidates = hardwareAccelerationCandidates,
        timestamps = { duration ->
            listOf(timestamp(duration))
        }).map(List<Frame.Video.Content?>::firstOrNull)
}