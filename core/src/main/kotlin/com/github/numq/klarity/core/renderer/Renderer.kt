package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface representing a renderer for video frames.
 * The renderer is responsible for managing frame dimensions, frame rate,
 * and rendering video content.
 */
interface Renderer {
    /**
     * The width of the rendered frames in pixels.
     */
    val width: Int

    /**
     * The height of the rendered frames in pixels.
     */
    val height: Int

    /**
     * The frame rate at which the video is rendered.
     */
    val frameRate: Double

    /**
     * An optional preview frame of the video content.
     * Can be null if no preview is set.
     */
    val preview: Frame.Video.Content?

    /**
     * A flow that emits the current frame of the video being rendered.
     * The frame can be null if no frame is currently available.
     */
    val frame: StateFlow<Frame.Video.Content?>

    /**
     * A flow that emits the current playback speed factor.
     */
    val playbackSpeedFactor: StateFlow<Float>

    /**
     * Sets the playback speed factor for the renderer.
     *
     * @param factor The new speed factor to set. Must be positive.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun setPlaybackSpeed(factor: Float): Result<Unit>

    /**
     * Draws the given frame content in the renderer.
     *
     * @param frame The video content frame to render.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun draw(frame: Frame.Video.Content): Result<Unit>

    /**
     * Resets the renderer to its initial state.
     * This typically includes restoring the preview frame.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun reset(): Result<Unit>

    /**
     * Closes the renderer.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun close(): Result<Unit>

    companion object {
        /**
         * Creates a new instance of a Renderer.
         *
         * @param width The width of the frames in pixels.
         * @param height The height of the frames in pixels.
         * @param frameRate The frame rate at which to render the video.
         * @param preview An optional preview frame of the video content.
         *
         * @return A [Result] containing either a new [Renderer] instance or an error if creation fails.
         */
        internal fun create(
            width: Int,
            height: Int,
            frameRate: Double,
            preview: Frame.Video.Content?,
        ): Result<Renderer> = runCatching {
            DefaultRenderer(width = width, height = height, frameRate = frameRate, preview = preview)
        }
    }
}