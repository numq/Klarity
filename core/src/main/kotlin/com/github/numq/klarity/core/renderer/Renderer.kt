package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface representing a renderer for video frames.
 */
interface Renderer {
    /**
     * The video format used.
     */
    val format: VideoFormat

    /**
     * A flow that emits the current frame of the video being rendered.
     */
    val frame: SharedFlow<Frame.Video.Content>

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
         * @param format The video format used.
         * @param preview An optional preview frame of the video content.
         *
         * @return A [Result] containing either a new [Renderer] instance or an error if creation fails.
         */
        internal fun create(format: VideoFormat, preview: Frame.Video.Content?): Result<Renderer> = runCatching {
            DefaultRenderer(format = format, preview = preview)
        }
    }
}