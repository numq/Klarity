package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.renderer.Renderer
import kotlin.time.Duration

/**
 * Provides real-time frame capture functionality for video media at specified timestamps, displays the captured frames through an attached renderer.
 */
interface PreviewManager {
    /**
     * Attaches a renderer to display preview frames. Only one renderer may be attached at a time.
     * Any previously attached renderer will be automatically detached.
     *
     * @param renderer The renderer implementation that will receive video frames
     * @throws PreviewManagerException if the renderer cannot be attached
     */
    fun attachRenderer(renderer: Renderer)

    /**
     * Detaches the current renderer if one is attached. After detachment, preview frames
     * will not be displayed until a new renderer is attached.
     *
     * @throws PreviewManagerException if detachment fails
     */
    fun detachRenderer()

    /**
     * Seeks to the specified timestamp and renders the corresponding frame if a renderer is attached.
     * If no renderer is attached, the operation will complete successfully but no rendering will occur.
     *
     * @param timestamp Desired timestamp
     * @param debounceTime Minimum delay between consecutive requests
     * @param keyframesOnly If true, seeks only to keyframes (faster but less precise)
     * @return [Result] indicating success or containing failure information
     * @throws PreviewManagerException if seek or decode operation fails
     * @throws IllegalArgumentException if timestamp is out of bounds
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
     * @return [Result] indicating success or containing failure information
     * @throws PreviewManagerException if resources cannot be properly released
     */
    suspend fun close(): Result<Unit>

    companion object {
        /**
         * Creates a new PreviewManager instance for the specified media file.
         *
         * @param location Path or URI to media source
         * @param width Optional output width in pixels (keeps original if null)
         * @param height Optional output height in pixels (keeps original if null)
         * @param hardwareAccelerationCandidates Preferred acceleration methods in order
         * @return [Result] containing new PreviewManager or failure information
         * @throws PreviewManagerException if preview capture fails
         * @throws UnsupportedOperationException for unsupported media formats
         */
        fun create(
            location: String,
            width: Int? = null,
            height: Int? = null,
            hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        ): Result<PreviewManager> = runCatching {
            VideoDecoderFactory().create(
                parameters = VideoDecoderFactory.Parameters(
                    location = location,
                    width = width,
                    height = height,
                    frameRate = null,
                    hardwareAccelerationCandidates = hardwareAccelerationCandidates
                )
            ).mapCatching { decoder ->
                DefaultPreviewManager(videoDecoder = decoder)
            }.getOrThrow()
        }
    }
}