package renderer

import scale.ImageScale

sealed interface Background {
    val scale: ImageScale

    data object Transparent : Background {
        override val scale: ImageScale = ImageScale.None
    }

    data class Color(val a: Int, val r: Int, val g: Int, val b: Int) : Background {
        override val scale: ImageScale = ImageScale.None
    }

    data class Blur(
        val sigma: Float = 2f,
        override val scale: ImageScale = ImageScale.Crop,
    ) : Background
}