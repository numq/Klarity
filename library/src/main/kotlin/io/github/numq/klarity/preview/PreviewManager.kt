package io.github.numq.klarity.preview

import io.github.numq.klarity.renderable.Renderable
import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.format.VideoFormat
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.pool.PoolFactory
import org.jetbrains.skia.Data
import kotlin.time.Duration

/**
 * Provides real-time frame capture functionality for video media at specified timestamps, displays the captured frames through an attached renderer.
 */
interface PreviewManager : Renderable {
    /**
     * Video format of the media used.
     */
    val format: VideoFormat

    /**
     * Seeks to the specified timestamp and renders the corresponding frame if a renderer is attached.
     * If no renderer is attached, the operation will complete successfully but no rendering will occur.
     *
     * @param timestamp desired timestamp
     * @param debounceTime minimum delay between consecutive requests
     * @param keyframesOnly if true, seeks only to keyframes (faster but less precise)
     *
     * @return [Result] indicating success
     */
    suspend fun preview(
        timestamp: Duration,
        debounceTime: Duration = Duration.ZERO,
        keyframesOnly: Boolean = false,
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
                location = location,
                hardwareAccelerationCandidates = hardwareAccelerationCandidates
            )
        ).mapCatching { decoder ->
            PoolFactory().create(
                parameters = PoolFactory.Parameters(
                    poolCapacity = POOL_CAPACITY,
                    createData = {
                        Data.makeUninitialized(decoder.media.videoFormat.bufferCapacity)
                    }
                )
            ).mapCatching { pool ->
                DefaultPreviewManager(decoder = decoder, pool = pool)
            }.getOrThrow()
        }
    }
}