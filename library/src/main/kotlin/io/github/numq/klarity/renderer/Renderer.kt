package io.github.numq.klarity.renderer

import io.github.numq.klarity.frame.Frame
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Surface

/**
 * A renderer for drawing video frames.
 */
sealed interface Renderer {
    /**
     * Width of the rendering content in pixels.
     */
    val width: Int

    /**
     * Height of the rendering content in pixels.
     */
    val height: Int

    /**
     * A state flow that emits the current generation ID of the [Surface].
     * Changes when the [Surface] is updated or cleared.
     */
    val generationId: StateFlow<Int>

    /**
     * Indicates whether the renderer currently draws nothing (e.g. after a flush or before rendering).
     *
     * @return `true` if no frame is currently rendered, `false` otherwise
     */
    fun drawsNothing(): Boolean

    /**
     * Draws directly to the Skia [Surface] using the provided callback.
     *
     * @param callback a function that receives the [Surface] for custom drawing
     */
    fun draw(callback: (Surface) -> Unit)

    /**
     * Requests rendering of the [Frame.Content.Video].
     *
     * @param frame [Frame.Content.Video] for rendering.
     *
     * @return [Result] indicating success or failure of the operation.
     */
    suspend fun render(frame: Frame.Content.Video): Result<Unit>

    /**
     * Flushes the Skia [Surface], finalizing any pending draw operations, resets the generation ID.
     *
     * @return [Result] indicating success or failure of the operation
     */
    suspend fun flush(): Result<Unit>

    /**
     * Closes the renderer and all dependent components, freeing resources.
     */
    suspend fun close(): Result<Unit>

    companion object {
        /**
         * Factory method to create a [Renderer] for a given video format.
         *
         * @param width the width of the rendering content in pixels
         * @param height the height of the rendering content in pixels
         *
         * @return [Result] containing either a new renderer instance or an error if creation fails
         */
        fun create(width: Int, height: Int): Result<Renderer> = runCatching {
            SkiaRenderer(width = width, height = height)
        }
    }
}