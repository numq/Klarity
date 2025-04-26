package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.flow.Flow
import java.io.Closeable

/**
 * Interface representing a video frame renderer that allows frame rendering.
 *
 * The renderer validates that frames match its configured video format before processing them.
 *
 * The renderer must be properly closed to release resources.
 */
interface Renderer : Closeable {
    /**
     * The video format that this renderer expects for incoming frames.
     *
     * Frames that don't match this format (width and height) will be silently ignored during rendering.
     */
    val format: VideoFormat

    /**
     * A flow containing the current frame with video content
     */
    val frame: Flow<Frame.Content.Video>

    /**
     * Renders the given video frame if it matches the renderer's format.
     *
     * Invalid frames are silently ignored.
     *
     * @param frame The video frame content to render.
     */
    fun render(frame: Frame.Content.Video)

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
            ChannelRenderer(format = format)
        }
    }
}