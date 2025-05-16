package renderer.compose

import androidx.compose.ui.geometry.Size
import io.github.numq.klarity.renderer.compose.ImageScale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageScaleTest {
    @Test
    fun `crop scale`() {
        val srcSize = Size(50f, 100f)
        assertEquals(
            Size(10f, 20f),
            ImageScale.Crop.scale(srcSize, Size(10f, 20f))
        )
        assertEquals(
            Size(20f, 40f),
            ImageScale.Crop.scale(srcSize, Size(20f, 10f))
        )
    }

    @Test
    fun `fill scale`() {
        val srcSize = Size(50f, 100f)
        assertEquals(
            Size(10f, 20f),
            ImageScale.Fill.scale(srcSize, Size(10f, 20f))
        )
        assertEquals(
            Size(20f, 40f),
            ImageScale.Fill.scale(srcSize, Size(20f, 40f))
        )
    }

    @Test
    fun `fit scale`() {
        val srcSize = Size(50f, 100f)
        assertEquals(
            Size(10f, 20f),
            ImageScale.Fit.scale(srcSize, Size(10f, 20f))
        )
        assertEquals(
            Size(5f, 10f),
            ImageScale.Fit.scale(srcSize, Size(20f, 10f))
        )
    }

    @Test
    fun `fit width scale`() {
        val srcSize = Size(50f, 100f)
        assertEquals(
            Size(10f, 20f),
            ImageScale.FitWidth.scale(srcSize, Size(10f, 20f))
        )
        assertEquals(
            Size(20f, 40f),
            ImageScale.FitWidth.scale(srcSize, Size(20f, 10f))
        )
    }

    @Test
    fun `fit height scale`() {
        val srcSize = Size(50f, 100f)
        assertEquals(
            Size(10f, 20f),
            ImageScale.FitHeight.scale(srcSize, Size(10f, 20f))
        )
        assertEquals(
            Size(5f, 10f),
            ImageScale.FitHeight.scale(srcSize, Size(20f, 10f))
        )
    }
}