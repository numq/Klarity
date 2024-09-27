package renderer

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import scale.ImageScale

/**
 * Represents different types of foreground elements in the renderer.
 * Each foreground defines its own scaling behavior.
 */
sealed interface Foreground {
    /**
     * Defines the scaling method for the foreground image.
     */
    val scale: ImageScale

    /**
     * Represents an empty foreground with no image to render.
     */
    data object Empty : Foreground {
        override val scale = ImageScale.None
    }

    /**
     * Represents a foreground where the image is provided by a renderer source.
     *
     * @property renderer The renderer that supplies the image.
     */
    data class Source(val renderer: Renderer, override val scale: ImageScale = ImageScale.Fit) : Foreground

    /**
     * Represents a foreground based on a specific frame of a video content.
     *
     * @property frame The video frame content used as the foreground image.
     */
    data class Frame(
        val frame: frame.Frame.Video.Content,
        override val scale: ImageScale = ImageScale.Fit,
    ) : Foreground

    /**
     * Represents a foreground created from raw image bytes.
     *
     * @property bytes The byte array representing the image data.
     * @property width The width of the image.
     * @property height The height of the image.
     * @property colorType The color type of the image (default is BGRA 8888).
     * @property alphaType The alpha type used in the image (default is pre-multiplied).
     */
    data class Cover(
        val bytes: ByteArray,
        val width: Int,
        val height: Int,
        val colorType: ColorType = ColorType.BGRA_8888,
        val alphaType: ColorAlphaType = ColorAlphaType.PREMUL,
        override val scale: ImageScale = ImageScale.Fit,
    ) : Foreground {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Cover

            if (!bytes.contentEquals(other.bytes)) return false
            if (width != other.width) return false
            if (height != other.height) return false
            if (colorType != other.colorType) return false
            if (alphaType != other.alphaType) return false
            if (scale != other.scale) return false

            return true
        }

        override fun hashCode(): Int {
            var result = bytes.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + colorType.hashCode()
            result = 31 * result + alphaType.hashCode()
            result = 31 * result + scale.hashCode()
            return result
        }
    }
}