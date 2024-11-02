package scale

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import com.github.numq.klarity.compose.scale.ImageScale
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class ImageScaleTest {
    @Test
    fun `crop scale`() {
        val srcSize = DpSize(50.dp, 100.dp)
        assertEquals(
            DpSize(10.dp, 20.dp),
            ImageScale.Crop.scaleDp(srcSize, DpSize(10.dp, 20.dp))
        )
        assertEquals(
            DpSize(20.dp, 40.dp),
            ImageScale.Crop.scaleDp(srcSize, DpSize(20.dp, 10.dp))
        )
    }

    @Test
    fun `fill scale`() {
        val srcSize = DpSize(50.dp, 100.dp)
        assertEquals(
            DpSize(10.dp, 20.dp),
            ImageScale.Fill.scaleDp(srcSize, DpSize(10.dp, 20.dp))
        )
        assertEquals(
            DpSize(20.dp, 40.dp),
            ImageScale.Fill.scaleDp(srcSize, DpSize(20.dp, 10.dp))
        )
    }

    @Test
    fun `fit scale`() {
        val srcSize = DpSize(50.dp, 100.dp)
        assertEquals(
            DpSize(10.dp, 20.dp),
            ImageScale.Fit.scaleDp(srcSize, DpSize(10.dp, 20.dp))
        )
        assertEquals(
            DpSize(5.dp, 10.dp),
            ImageScale.Fit.scaleDp(srcSize, DpSize(20.dp, 10.dp))
        )
    }

    @Test
    fun `fit width scale`() {
        val srcSize = DpSize(50.dp, 100.dp)
        assertEquals(
            DpSize(10.dp, 20.dp),
            ImageScale.FitWidth.scaleDp(srcSize, DpSize(10.dp, 20.dp))
        )
        assertEquals(
            DpSize(20.dp, 40.dp),
            ImageScale.FitWidth.scaleDp(srcSize, DpSize(20.dp, 10.dp))
        )
    }

    @Test
    fun `fit height scale`() {
        val srcSize = DpSize(50.dp, 100.dp)
        assertEquals(
            DpSize(10.dp, 20.dp),
            ImageScale.FitHeight.scaleDp(srcSize, DpSize(10.dp, 20.dp))
        )
        assertEquals(
            DpSize(5.dp, 10.dp),
            ImageScale.FitHeight.scaleDp(srcSize, DpSize(20.dp, 10.dp))
        )
    }
}