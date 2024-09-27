package renderer

import scale.ImageScale

/**
 * Represents different types of background in the renderer.
 * Each background can define how it scales the image.
 */
sealed interface Background {
    /**
     * Defines the scaling method for the background.
     */
    val scale: ImageScale

    /**
     * Represents a transparent background.
     * The background is fully transparent with no scaling.
     */
    data object Transparent : Background {
        override val scale: ImageScale = ImageScale.None
    }

    /**
     * Represents a solid color background.
     *
     * @property a Alpha channel value (0-255).
     * @property r Red channel value (0-255).
     * @property g Green channel value (0-255).
     * @property b Blue channel value (0-255).
     */
    data class Color(val a: Int, val r: Int, val g: Int, val b: Int) : Background {
        override val scale: ImageScale = ImageScale.None
    }

    /**
     * Represents a blurred background.
     *
     * @property sigma The blur intensity applied to the background.
     * @property scale Specifies how the blurred image should be scaled.
     */
    data class Blur(
        val sigma: Float = 2f,
        override val scale: ImageScale = ImageScale.Crop,
    ) : Background
}