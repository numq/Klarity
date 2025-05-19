package io.github.numq.klarity.renderer

import io.github.numq.klarity.format.VideoFormat
import io.github.numq.klarity.frame.Frame
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Surface

/**
 * A renderer for drawing video frames.
 */
interface Renderer {
    /**
     * A video format used by the renderer.
     */
    val format: VideoFormat

    /**
     * A state flow that emits the current generation ID of the [Surface].
     * Changes when the [Surface] is updated or cleared.
     */
    val generationId: StateFlow<Int>

    /**
     * Indicates whether the renderer currently draws nothing (e.g. after a flush or before rendering).
     *
     * @return true if no frame is currently rendered, false otherwise.
     */
    fun drawsNothing(): Boolean

    /**
     * Draws directly to the Skia [Surface] using the provided callback.
     *
     * @param callback A function that receives the [Surface] for custom drawing.
     */
    fun draw(callback: (Surface) -> Unit)

    /**
     * Requests rendering of the [Frame.Content.Video].
     *
     * @param frame A [Frame.Content.Video] for rendering.
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun render(frame: Frame.Content.Video): Result<Unit>

    /**
     * Flushes the Skia [Surface], finalizing any pending draw operations, resets the generation ID.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun flush(): Result<Unit>

    /**
     * Closes the renderer and all dependent components, freeing resources.
     */
    suspend fun close(): Result<Unit>

    companion object {
        /**
         * Factory method to create a [SkiaRenderer] for a given video format.
         *
         * @param format The target video format.
         * @return A [Result] containing either a new [SkiaRenderer] instance or an error if creation fails.
         */
        fun create(format: VideoFormat): Result<Renderer> = runCatching {
            SkiaRenderer(format = format)
        }
    }
}