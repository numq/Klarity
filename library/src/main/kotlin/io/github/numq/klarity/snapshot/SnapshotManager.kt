package io.github.numq.klarity.snapshot

import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.pool.PoolFactory
import io.github.numq.klarity.renderer.Renderer
import org.jetbrains.skia.Data
import kotlin.time.Duration

/**
 * Provides frame capture functionality for video media at specified timestamps.
 */
object SnapshotManager {
    private const val POOL_CAPACITY = 1

    /**
     * Captures multiple video frames at specified timestamps.
     *
     * @param location Media file path or URI
     * @param hardwareAccelerationCandidates Preferred hardware acceleration methods in order of priority
     * @param keyframesOnly If true, seeks only to keyframes (faster but less precise)
     * @param timestamps A function that provides a media duration that can be used to construct desired timestamps
     * @return [Result] Indicating success or containing failure information
     * @throws SnapshotManagerException if frame capture fails
     */
    suspend fun snapshots(
        location: String,
        renderer: Renderer,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        keyframesOnly: Boolean = true,
        timestamps: (duration: Duration) -> (List<Duration>) = { listOf(Duration.ZERO) },
    ) = VideoDecoderFactory().create(
        parameters = VideoDecoderFactory.Parameters(
            location = location,
            hardwareAccelerationCandidates = hardwareAccelerationCandidates
        )
    ).mapCatching { decoder ->
        val validTimestamps = timestamps(decoder.media.duration).distinct().filter {
            it in Duration.ZERO..decoder.media.duration
        }

        if (validTimestamps.isNotEmpty()) {
            PoolFactory().create(
                parameters = PoolFactory.Parameters(
                    poolCapacity = POOL_CAPACITY,
                    createData = {
                        Data.makeUninitialized(decoder.media.videoFormat.bufferCapacity)
                    }
                )
            ).mapCatching { pool ->
                try {
                    validTimestamps.forEach { timestamp ->
                        if (timestamp.isPositive()) {
                            decoder.seekTo(timestamp = timestamp, keyframesOnly = keyframesOnly).getOrThrow()
                        }

                        val data = pool.acquire().getOrThrow()

                        try {
                            (decoder.decodeVideo(data = data).getOrThrow() as? Frame.Content.Video)?.let { frame ->
                                renderer.save(frame).getOrThrow()
                            }
                        } finally {
                            pool.release(item = data).getOrThrow()
                        }
                    }
                } finally {
                    decoder.close().getOrThrow()

                    pool.close().getOrThrow()
                }
            }
        }
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
     * @return [Result] Indicating success or containing failure information
     * @throws SnapshotManagerException if frame capture fails
     */
    suspend fun snapshot(
        location: String,
        renderer: Renderer,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        keyframesOnly: Boolean = true,
        timestamp: (duration: Duration) -> (Duration) = { Duration.ZERO },
    ) = snapshots(
        location = location,
        renderer = renderer,
        keyframesOnly = keyframesOnly,
        hardwareAccelerationCandidates = hardwareAccelerationCandidates,
        timestamps = { duration ->
            listOf(timestamp(duration))
        })
}