package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.compose.scale.ImageScale
import com.github.numq.klarity.core.renderer.Renderer
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType

/**
 * Represents different types of foreground elements in the renderer.
 * Each foreground defines its own scaling behavior.
 */
sealed interface Foreground {
    /**
     * Defines the scaling method for the foreground image.
     */
    val imageScale: ImageScale

    /**
     * Represents an empty foreground with no image to render.
     */
    data object Empty : Foreground {
        override val imageScale = ImageScale.None
    }

    /**
     * Represents a foreground where the image is provided by a renderer source.
     *
     * @property renderer The renderer that supplies the image.
     * @property imageScale The video frame scaling method (default is [ImageScale.Fit]).
     */
    data class Source(val renderer: Renderer, override val imageScale: ImageScale = ImageScale.Fit) : Foreground

    /**
     * Represents a foreground based on a specific frame of a video content.
     *
     * @property frame The video frame content used as the foreground image.
     * @property imageScale The video frame scaling method (default is [ImageScale.Fit]).
     */
    data class Frame(
        val frame: com.github.numq.klarity.core.frame.Frame.Video.Content,
        override val imageScale: ImageScale = ImageScale.Fit,
    ) : Foreground

    /**
     * Represents a foreground created from raw image bytes.
     *
     * @property bytes The byte array representing the image data.
     * @property width The width of the image.
     * @property height The height of the image.
     * @property colorType The color type of the image (default is [ColorType.BGRA_8888]).
     * @property alphaType The alpha type used in the image (default is [ColorAlphaType.UNPREMUL]).
     * @property imageScale The video frame scaling method (default is [ImageScale.Fit]).
     */
    data class Image(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val colorType: ColorType = ColorType.BGRA_8888,
        val alphaType: ColorAlphaType = ColorAlphaType.UNPREMUL,
        override val imageScale: ImageScale = ImageScale.Fit,
    ) : Foreground {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Image

            if (!bytes.contentEquals(other.bytes)) return false
            if (width != other.width) return false
            if (height != other.height) return false
            if (colorType != other.colorType) return false
            if (alphaType != other.alphaType) return false
            if (imageScale != other.imageScale) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + colorType.hashCode()
            result = 31 * result + alphaType.hashCode()
            result = 31 * result + imageScale.hashCode()
            return result
        }
    }
}