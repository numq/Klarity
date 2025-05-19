package io.github.numq.klarity.snapshot

import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import org.jetbrains.skia.Data
import kotlin.time.Duration

/**
 * Provides frame capture functionality for video media at specified timestamps.
 */
object SnapshotManager {
    /**
     * Captures multiple video frames at specified timestamps.
     *
     * @param location Media file path or URI
     * @param hardwareAccelerationCandidates Preferred hardware acceleration methods in order of priority
     * @param keyframesOnly If true, seeks only to keyframes (faster but less precise)
     * @param timestamps A function that provides a media duration that can be used to construct desired timestamps
     * @return [Result] Containing a list of [Frame.Content.Video] that should be closed, otherwise failure information
     * @throws SnapshotManagerException if frame capture fails
     */
    suspend fun snapshots(
        location: String,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        keyframesOnly: Boolean = true,
        timestamps: (duration: Duration) -> (List<Duration>) = { listOf(Duration.ZERO) },
    ): Result<List<Frame.Content.Video>> = VideoDecoderFactory().create(
        parameters = VideoDecoderFactory.Parameters(
            location = location, hardwareAccelerationCandidates = hardwareAccelerationCandidates
        )
    ).mapCatching { decoder ->
        val frames = mutableListOf<Frame.Content.Video>()

        try {
            timestamps(decoder.media.duration).filter {
                it in Duration.ZERO..decoder.media.duration
            }.forEach { timestamp ->
                decoder.seekTo(timestamp = timestamp, keyframesOnly = keyframesOnly).getOrThrow()

                var frame: Frame.Content.Video? = null

                Data.makeUninitialized(decoder.media.videoFormat.bufferCapacity).let { data ->
                    try {
                        frame = (decoder.decodeVideo(data = data).getOrThrow() as? Frame.Content.Video)
                    } finally {
                        if (frame == null) {
                            data.close()
                        }
                    }
                }

                frame?.let(frames::add)
            }
        } catch (t: Throwable) {
            frames.forEach(Frame.Content.Video::close)

            throw t
        } finally {
            decoder.close()
        }

        frames
    }.recoverCatching { t ->
        throw SnapshotManagerException(t)
    }

    /**
     * Captures a single video frame at specified timestamp.
     *
     * @param location Media file path or URI
     * @param hardwareAccelerationCandidates Preferred hardware acceleration methods in order of priority
     * @param keyframesOnly If true, seeks only to keyframes (faster but less precise)
     * @param timestamp A function that provides a media duration that can be used to construct desired timestamp
     * @return [Result] Containing a [Frame.Content.Video] that should be closed or null, otherwise failure information
     * @throws SnapshotManagerException if frame capture fails
     */
    suspend fun snapshot(
        location: String,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        keyframesOnly: Boolean = true,
        timestamp: (duration: Duration) -> (Duration) = { Duration.ZERO },
    ): Result<Frame.Content.Video?> = snapshots(
        location = location,
        keyframesOnly = keyframesOnly,
        hardwareAccelerationCandidates = hardwareAccelerationCandidates,
        timestamps = { duration ->
            listOf(timestamp(duration))
        }).mapCatching { snapshots -> snapshots.firstOrNull() }
}