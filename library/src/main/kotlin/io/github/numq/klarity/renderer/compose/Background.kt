package io.github.numq.klarity.renderer.compose

/**
 * Represents different types of background in the renderer.
 * Each background can define how it scales the image.
 */
sealed interface Background {
    /**
     * Defines the scaling method for the background.
     */
    val imageScale: ImageScale

    /**
     * Represents a transparent background.
     * The background is fully transparent with no scaling.
     */
    data object Transparent : Background {
        override val imageScale: ImageScale = ImageScale.None
    }

    /**
     * Represents a solid color background.
     *
     * @property red red channel value (0-255)
     * @property green green channel value (0-255)
     * @property blue blue channel value (0-255)
     * @property alpha alpha channel value (0-255)
     */
    data class Color(val red: Int, val green: Int, val blue: Int, val alpha: Int) : Background {
        override val imageScale: ImageScale = ImageScale.None
    }

    /**
     * Represents a blurred background.
     *
     * @property sigmaX the X axis blur intensity applied to the background
     * @property sigmaY the Y axis blur intensity applied to the background
     * @property imageScale the video frame scaling method (default is [ImageScale.Crop])
     */
    data class Blur(
        val sigmaX: Float = 8f,
        val sigmaY: Float = 8f,
        override val imageScale: ImageScale = ImageScale.Crop,
    ) : Background
}