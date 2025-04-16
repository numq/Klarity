package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import java.io.Closeable

/**
 * Interface representing a renderer for video frames.
 */
interface Renderer : Closeable {
    /**
     * The video format used.
     */
    val format: VideoFormat

    /**
     * Sets a callback for rendering a frame.
     *
     * @param callback The callback for rendering a frame.
     */
    fun onRender(callback: (Frame.Video.Content) -> Unit)

    /**
     * Renders the given frame content in the renderer.
     *
     * @param frame The video content frame to render.
     */
    fun render(frame: Frame.Video.Content)

    companion object {
        /**
         * Creates a new instance of a Renderer.
         *
         * @param format The video format of rendered frames.
         *
         * @return A [Result] containing either a new [Renderer] instance or an error if creation fails.
         */
        fun create(format: VideoFormat): Result<Renderer> = runCatching {
            DefaultRenderer(format = format)
        }
    }
}