package scale

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

abstract class ImageScale {
    object Fit : ImageScale() {
        override fun scaleDp(
            srcWidth: Dp,
            srcHeight: Dp,
            dstWidth: Dp,
            dstHeight: Dp,
        ): Pair<DpOffset, DpSize> {

            require(srcWidth > 0.dp && srcHeight > 0.dp) { "Source dimensions must be positive." }

            val scaleX = dstWidth / srcWidth
            val scaleY = dstHeight / srcHeight

            val scale = minOf(scaleX, scaleY)

            val resizedWidth = srcWidth * scale
            val resizedHeight = srcHeight * scale

            val offsetX = (dstWidth - resizedWidth) / 2
            val offsetY = (dstHeight - resizedHeight) / 2

            val offset = DpOffset(offsetX, offsetY)
            val size = DpSize(resizedWidth, resizedHeight)

            return offset to size
        }
    }

    abstract fun scaleDp(srcWidth: Dp, srcHeight: Dp, dstWidth: Dp, dstHeight: Dp): Pair<DpOffset, DpSize>
}