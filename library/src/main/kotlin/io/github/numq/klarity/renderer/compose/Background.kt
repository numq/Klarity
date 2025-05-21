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
     * @property a alpha channel value (0-255)
     * @property r red channel value (0-255)
     * @property g green channel value (0-255)
     * @property b blue channel value (0-255)
     */
    data class Color(val a: Int, val r: Int, val g: Int, val b: Int) : Background {
        override val imageScale: ImageScale = ImageScale.None
    }

    /**
     * Represents a blurred background.
     *
     * @property sigma the blur intensity applied to the background
     * @property imageScale the video frame scaling method (default is [ImageScale.Crop])
     */
    data class Blur(
        val sigma: Float = 8f,
        override val imageScale: ImageScale = ImageScale.Crop,
    ) : Background
}