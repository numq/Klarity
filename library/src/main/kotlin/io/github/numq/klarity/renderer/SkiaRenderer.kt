package io.github.numq.klarity.renderer

import io.github.numq.klarity.format.VideoFormat
import io.github.numq.klarity.frame.Frame
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Surface

/**
 * A renderer implementation using Skia for drawing video frames.
 */
interface SkiaRenderer : Renderer {
    /**
     * A state flow that emits the current generation ID of the rendered surface.
     * Changes when the surface is updated or cleared.
     */
    val generationId: StateFlow<Int>

    /**
     * Indicates whether the renderer currently draws nothing (e.g. after a flush or before rendering).
     *
     * @return true if no frame is currently rendered, false otherwise.
     */
    fun drawsNothing(): Boolean

    /**
     * Draws directly to the Skia surface using the provided callback.
     *
     * @param callback A function that receives the surface for custom drawing.
     */
    fun draw(callback: (Surface) -> Unit)

    /**
     * Provides access to cached frames, returning the result of the given callback.
     *
     * @param callback Suspend function that receives a list of cached frames and returns a result.
     * @return The result returned by the callback.
     */
    suspend fun <T> withCache(callback: suspend (List<Frame.Content.Video>) -> T): T

    /**
     * Flushes the Skia surface, finalizing any pending draw operations, resets the generation ID.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun flush(): Result<Unit>

    companion object {
        /**
         * Factory method to create a [SkiaRenderer] for a given video format.
         *
         * @param format The target video format.
         * @return A [Result] containing either a new [SkiaRenderer] instance or an error if creation fails.
         */
        fun create(format: VideoFormat): Result<SkiaRenderer> = runCatching {
            DefaultSkiaRenderer(format = format)
        }
    }
}