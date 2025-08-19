package io.github.numq.klarity.preview

import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.format.Format
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.pool.PoolFactory
import io.github.numq.klarity.renderer.Renderer
import org.jetbrains.skia.Data
import kotlin.time.Duration

/**
 * Provides real-time frame capture functionality for video media at specified timestamps.
 */
interface PreviewManager {
    /**
     * Video format of the media used.
     */
    val format: Format.Video

    /**
     * Seeks to the specified timestamp and renders the corresponding frame.
     *
     * @param renderer the renderer to display frame
     * @param timestamp desired timestamp
     * @param keyFramesOnly if true, seeks only to keyframes (faster but less precise)
     *
     * @return [Result] indicating success
     */
    suspend fun preview(
        renderer: Renderer,
        timestamp: Duration,
        keyFramesOnly: Boolean = false,
    ): Result<Unit>

    /**
     * Releases all resources and stops any ongoing preview operations.
     * The instance should not be used after closing.
     *
     * @return [Result] indicating success
     */
    suspend fun close(): Result<Unit>

    companion object {
        private const val POOL_CAPACITY = 2

        /**
         * Creates a new [PreviewManager] instance for the specified media file.
         *
         * @param location path or URI to media source
         * @param hardwareAccelerationCandidates preferred acceleration methods in order
         *
         * @return [Result] containing [PreviewManager] instance
         */
        fun create(
            location: String,
            hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        ): Result<PreviewManager> = VideoDecoderFactory().create(
            parameters = VideoDecoderFactory.Parameters(
                location = location, hardwareAccelerationCandidates = hardwareAccelerationCandidates
            )
        ).mapCatching { decoder ->
            PoolFactory().create(
                parameters = PoolFactory.Parameters(
                    poolCapacity = POOL_CAPACITY, createData = {
                        Data.makeUninitialized(decoder.format.bufferCapacity)
                    })
            ).mapCatching { pool ->
                DefaultPreviewManager(decoder = decoder, pool = pool)
            }.getOrThrow()
        }
    }
}