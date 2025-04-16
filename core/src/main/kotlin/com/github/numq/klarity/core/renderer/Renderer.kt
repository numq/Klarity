package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import java.io.Closeable

/**
 * Interface representing a video frame renderer that allows frame rendering through callbacks.
 *
 * The renderer validates that frames match its configured video format before processing them.
 *
 * Clients can register a callback to handle rendered frames.
 *
 * The renderer must be properly closed to release resources and clear the callback.
 */
interface Renderer : Closeable {
    /**
     * The video format that this renderer expects for incoming frames.
     *
     * Frames that don't match this format (width and height) will be silently ignored during rendering.
     */
    val format: VideoFormat

    /**
     * Registers a callback to be invoked when frames are rendered.
     *
     * Only one callback can be registered at a time.
     *
     * Subsequent calls will replace the previous callback.
     *
     * The callback will only be invoked for frames that match the renderer's format.
     *
     * @param callback The function to invoke when a valid frame is rendered, or `null`
     *                 to remove the current callback. The callback receives the rendered
     *                 video frame content.
     */
    fun onRender(callback: (Frame.Video.Content) -> Unit)

    /**
     * Renders the given video frame if it matches the renderer's format.
     *
     * If the frame's dimensions match the renderer's format and a callback is registered,
     * the callback will be invoked with the frame content.
     *
     * Invalid frames are silently ignored.
     *
     * @param frame The video frame content to render.
     */
    fun render(frame: Frame.Video.Content)

    companion object {
        /**
         * Creates a new Renderer instance configured for the specified video format.
         *
         * @param format The video format that the renderer will accept. Frames must match
         *               this format's width and height to be rendered.
         * @return A [Result] containing either the new [Renderer] instance or an error if
         *         creation fails.
         */
        fun create(format: VideoFormat): Result<Renderer> = runCatching {
            DefaultRenderer(format = format)
        }
    }
}