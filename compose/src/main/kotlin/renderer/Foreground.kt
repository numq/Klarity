package renderer

import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import scale.ImageScale

sealed interface Foreground {
    val scale: ImageScale

    data object Empty : Foreground {
        override val scale = ImageScale.None
    }

    data class Source(val renderer: Renderer, override val scale: ImageScale = ImageScale.Fit) : Foreground

    data class Frame(
        val frame: frame.Frame.Video.Content,
        override val scale: ImageScale = ImageScale.Fit,
    ) : Foreground

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