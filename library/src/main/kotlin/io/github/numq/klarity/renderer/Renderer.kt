package io.github.numq.klarity.renderer

import io.github.numq.klarity.frame.Frame
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Canvas
import org.jetbrains.skia.Paint
import org.jetbrains.skia.Rect
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
     * A state flow that indicates whether the renderer currently draws nothing (e.g. after a flush or before rendering).
     *
     * @return `true` if no frame is currently rendered, `false` otherwise
     */
    val drawsNothing: StateFlow<Boolean>

    /**
     * Renders the current frame to the target Skia [Canvas] with the specified background.
     *
     * @param canvas the Skia [Canvas] to render onto
     * @param backgroundRect the rectangle area for background effects
     * @param backgroundColorPaint the paint for background color drawing
     * @param backgroundBlurPaint the paint for background blur drawing
     * @param foregroundRect the rectangle area for the video frame
     */
    fun onRender(
        canvas: Canvas,
        backgroundRect: Rect,
        backgroundColorPaint: Paint?,
        backgroundBlurPaint: Paint?,
        foregroundRect: Rect
    )

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