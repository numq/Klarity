package io.github.numq.klarity.preview

import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.format.VideoFormat
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.pool.PoolFactory
import io.github.numq.klarity.renderer.Renderer
import org.jetbrains.skia.Data
import kotlin.time.Duration

/**
 * Provides real-time frame capture functionality for video media at specified timestamps, displays the captured frames through an attached renderer.
 */
interface PreviewManager {
    /**
     * Video format of the media used.
     */
    val format: VideoFormat

    /**
     * Attaches a renderer to display preview frames. Only one renderer may be attached at a time.
     * Any previously attached renderer will be automatically detached.
     *
     * @param renderer The renderer implementation that will receive video frames
     */
    fun attachRenderer(renderer: Renderer)

    /**
     * Detaches the current renderer if one is attached. After detachment, preview frames
     * will not be displayed until a new renderer is attached.
     */
    fun detachRenderer()

    /**
     * Seeks to the specified timestamp and renders the corresponding frame if a renderer is attached.
     * If no renderer is attached, the operation will complete successfully but no rendering will occur.
     *
     * @param timestamp Desired timestamp
     * @param debounceTime Minimum delay between consecutive requests
     * @param keyframesOnly If true, seeks only to keyframes (faster but less precise)
     * @return [Result] Indicating success or containing failure information
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
     * @return [Result] Indicating success or containing failure information
     */
    suspend fun close(): Result<Unit>

    companion object {
        private const val POOL_CAPACITY = 1

        /**
         * Creates a new PreviewManager instance for the specified media file.
         *
         * @param location Path or URI to media source
         * @param hardwareAccelerationCandidates Preferred acceleration methods in order
         * @return [Result] Containing new PreviewManager or failure information
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