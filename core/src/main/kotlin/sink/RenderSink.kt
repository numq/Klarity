package sink

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface representing a rendering sink that can receive pixel data and perform drawing operations.
 */
interface RenderSink {
    /**
     * Companion object providing a factory method to create instances of RenderSink.
     */
    companion object {
        /**
         * Creates a new instance of RenderSink using the default implementation.
         *
         * @return A new RenderSink instance.
         */
        fun create(): RenderSink = DefaultRenderSink()
    }

    /**
     * A StateFlow representing the pixel data to be rendered.
     * Observers can collect updates to the pixel data through this flow.
     */
    val pixels: StateFlow<ByteArray?>

    /**
     * Draws the specified pixel data.
     *
     * @param pixels The pixel data to be drawn.
     * @return `true` if the drawing operation was successful, `false` otherwise.
     */
    fun draw(pixels: ByteArray): Boolean

    /**
     * Erases the existing pixel data, resetting the rendering area.
     *
     * @return `true` if the erase operation was successful, `false` otherwise.
     */
    fun erase(): Boolean
}