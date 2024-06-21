package renderer

import scale.ImageScale

sealed interface Foreground {
    val scale: ImageScale

    data class Source(val renderer: Renderer, override val scale: ImageScale = ImageScale.Fit) : Foreground

    data class Cover(
        val width: Int,
        val height: Int,
        val bytes: ByteArray,
        override val scale: ImageScale = ImageScale.Fit,
    ) : Foreground {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Cover

            if (scale != other.scale) return false
            if (width != other.width) return false
            if (height != other.height) return false
            if (!bytes.contentEquals(other.bytes)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = scale.hashCode()
            result = 31 * result + width
            result = 31 * result + height
            result = 31 * result + bytes.contentHashCode()
            return result
        }

    }
}