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
     * @param location media file path or URI
     * @param hardwareAccelerationCandidates preferred hardware acceleration methods in order of priority
     * @param keyframesOnly if `true`, seeks only to keyframes (faster but less precise)
     * @param timestamps a function that provides a media duration that can be used to construct desired timestamps
     *
     * @return [Result] containing a list of [Frame.Content.Video]. Each [Frame.Content.Video] must be closed by the caller
     *
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
     * @param location media file path or URI
     * @param hardwareAccelerationCandidates preferred hardware acceleration methods in order of priority
     * @param keyframesOnly if `true`, seeks only to keyframes (faster but less precise)
     * @param timestamp a function that provides a media duration that can be used to construct desired timestamp
     *
     * @return [Result] containing a [Frame.Content.Video] or null. [Frame.Content.Video] must be closed by the caller
     *
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