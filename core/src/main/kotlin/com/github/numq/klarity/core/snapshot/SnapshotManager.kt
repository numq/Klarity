package com.github.numq.klarity.core.snapshot

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.renderer.Renderer
import kotlin.time.Duration

/**
 * Provides frame capture functionality for video media at specified timestamps.
 */
object SnapshotManager {
    private const val MIN_FRAME_POOL_CAPACITY = 2

    /**
     * Captures multiple video frames at specified timestamps.
     *
     * @param location Media file path or URI
     * @param framePoolCapacity Capacity of the pool of frames pre-allocated by the decoder
     * @param width Optional target width (keeps original if null)
     * @param height Optional target height (keeps original if null)
     * @param hardwareAccelerationCandidates Preferred hardware acceleration methods in order of priority
     * @param keyframesOnly If true, seeks only to keyframes (faster but less precise)
     * @param timestamps A function that provides a media duration that can be used to construct desired timestamps
     * @return [Result] Indicating success or containing failure information
     * @throws SnapshotManagerException if frame capture fails
     */
    suspend fun snapshots(
        location: String,
        renderer: Renderer,
        framePoolCapacity: Int = MIN_FRAME_POOL_CAPACITY,
        width: Int? = null,
        height: Int? = null,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        keyframesOnly: Boolean = true,
        timestamps: (duration: Duration) -> (List<Duration>) = { listOf(Duration.ZERO) },
    ) = VideoDecoderFactory().create(
        parameters = VideoDecoderFactory.Parameters(
            location = location,
            framePoolCapacity = framePoolCapacity.coerceAtLeast(MIN_FRAME_POOL_CAPACITY),
            width = width,
            height = height,
            hardwareAccelerationCandidates = hardwareAccelerationCandidates
        )
    ).mapCatching { decoder ->
        try {
            timestamps(decoder.media.duration).distinct().filter {
                it in Duration.ZERO..decoder.media.duration
            }.forEach { timestamp ->
                decoder.seekTo(timestamp = timestamp, keyframesOnly = keyframesOnly).getOrDefault(Unit)

                (decoder.decode().getOrThrow() as? Frame.Content.Video)?.let { frame ->
                    renderer.save(frame).getOrThrow()
                }
            }
        } catch (t: Throwable) {
            throw t
        } finally {
            decoder.close().getOrThrow()
        }
    }.recoverCatching { t ->
        throw SnapshotManagerException(t)
    }

    /**
     * Captures a single video frame at specified timestamp.
     *
     * @param location Media file path or URI
     * @param framePoolCapacity Capacity of the pool of frames pre-allocated by the decoder
     * @param width Optional target width (maintains aspect ratio if null)
     * @param height Optional target height (maintains aspect ratio if null)
     * @param hardwareAccelerationCandidates Preferred hardware acceleration methods in order of priority
     * @param keyframesOnly If true, seeks only to keyframes (faster but less precise)
     * @param timestamp A function that provides a media duration that can be used to construct desired timestamp
     * @return [Result] Indicating success or containing failure information
     * @throws SnapshotManagerException if frame capture fails
     */
    suspend fun snapshot(
        location: String,
        renderer: Renderer,
        framePoolCapacity: Int = MIN_FRAME_POOL_CAPACITY,
        width: Int? = null,
        height: Int? = null,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        keyframesOnly: Boolean = true,
        timestamp: (duration: Duration) -> (Duration) = { Duration.ZERO },
    ) = snapshots(
        location = location,
        renderer = renderer,
        framePoolCapacity = framePoolCapacity,
        width = width,
        height = height,
        keyframesOnly = keyframesOnly,
        hardwareAccelerationCandidates = hardwareAccelerationCandidates,
        timestamps = { duration ->
            listOf(timestamp(duration))
        })
}