package io.github.numq.klarity.snapshot

import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import org.jetbrains.skia.Data
import kotlin.time.Duration

/**
 * Provides video snapshot capture functionality for video media at specified timestamps.
 */
object SnapshotManager {
    /**
     * Captures multiple video snapshots at specified timestamps.
     *
     * @param location media file path or URI
     * @param hardwareAccelerationCandidates preferred hardware acceleration methods in order of priority
     * @param keyFramesOnly if `true`, seeks only to keyframes (faster but less precise)
     * @param timestamps a function that provides a media duration that can be used to construct desired timestamps
     *
     * @return [Result] containing a list of [Snapshot]. Each [Snapshot] must be closed by the caller
     *
     * @throws SnapshotManagerException if snapshot capture fails
     */
    suspend fun snapshots(
        location: String,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        keyFramesOnly: Boolean = true,
        timestamps: (duration: Duration) -> (List<Duration>) = { listOf(Duration.ZERO) },
    ): Result<List<Snapshot>> = VideoDecoderFactory().create(
        parameters = VideoDecoderFactory.Parameters(
            location = location, hardwareAccelerationCandidates = hardwareAccelerationCandidates
        )
    ).mapCatching { decoder ->
        val format = decoder.format

        val frames = mutableListOf<Frame.Content.Video>()

        try {
            timestamps(decoder.duration).filter {
                it in Duration.ZERO..decoder.duration
            }.forEach { timestamp ->
                decoder.seekTo(timestamp = timestamp, keyFramesOnly = keyFramesOnly).getOrThrow()

                var frame: Frame.Content.Video? = null

                Data.makeUninitialized(format.bufferCapacity).let { data ->
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
            decoder.close().getOrThrow()
        }

        frames.map { frame ->
            Snapshot(format = format, frame = frame)
        }
    }.recoverCatching { t ->
        throw SnapshotManagerException(t)
    }

    /**
     * Captures a single video snapshot at specified timestamp.
     *
     * @param location media file path or URI
     * @param hardwareAccelerationCandidates preferred hardware acceleration methods in order of priority
     * @param keyFramesOnly if `true`, seeks only to keyframes (faster but less precise)
     * @param timestamp a function that provides a media duration that can be used to construct desired timestamp
     *
     * @return [Result] containing a [Snapshot] or null. [Snapshot] must be closed by the caller
     *
     * @throws SnapshotManagerException if snapshot capture fails
     */
    suspend fun snapshot(
        location: String,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        keyFramesOnly: Boolean = true,
        timestamp: (duration: Duration) -> (Duration) = { Duration.ZERO },
    ): Result<Snapshot?> = snapshots(
        location = location,
        keyFramesOnly = keyFramesOnly,
        hardwareAccelerationCandidates = hardwareAccelerationCandidates,
        timestamps = { duration ->
            listOf(timestamp(duration))
        }).mapCatching { snapshots -> snapshots.firstOrNull() }
}