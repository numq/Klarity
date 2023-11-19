package scale

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import kotlin.math.ceil

/**
 * An interface for defining image scaling operations.
 */
interface ImageScale {

    /**
     * Scales the source image dimensions to match the destination image dimensions.
     *
     * @param srcSize The dimensions of the source image.
     * @param dstSize The dimensions of the destination image.
     * @return The scaled dimensions of the source image.
     */
    fun scaleDp(srcSize: DpSize, dstSize: DpSize): DpSize

    companion object {

        /**
         * Validates that source and destination dimensions are positive.
         *
         * @param srcSize The dimensions of the source image.
         * @param dstSize The dimensions of the destination image.
         * @throws IllegalArgumentException if either source or destination dimensions are not positive.
         */
        private fun validateDimensions(
            srcSize: DpSize,
            dstSize: DpSize,
        ) {
            require(srcSize.width > 0.dp && srcSize.height > 0.dp) { "Source dimensions must be positive." }
            require(dstSize.width > 0.dp && dstSize.height > 0.dp) { "Destination dimensions must be positive." }
        }
    }

    /**
     * No scaling. Returns the destination dimensions unchanged.
     */
    object None : ImageScale {
        override fun scaleDp(srcSize: DpSize, dstSize: DpSize) = dstSize
    }

    /**
     * Scales the source uniformly to completely fill the destination.
     */
    object Crop : ImageScale {
        override fun scaleDp(srcSize: DpSize, dstSize: DpSize): DpSize {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)

            val scale = maxOf(dstSize.width / srcSize.width, dstSize.height / srcSize.height)

            val scaledWidth = ceil(srcSize.width.value * scale).dp
            val scaledHeight = ceil(srcSize.height.value * scale).dp

            return DpSize(scaledWidth, scaledHeight)
        }
    }

    /**
     * Scales the source to match the largest destination side while preserving aspect ratio.
     */
    object Fill : ImageScale {
        override fun scaleDp(srcSize: DpSize, dstSize: DpSize): DpSize {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)

            val scale = if (dstSize.width / dstSize.height > srcSize.width / srcSize.height) {
                dstSize.width / srcSize.width
            } else {
                dstSize.height / srcSize.height
            }

            val scaledWidth = ceil(srcSize.width.value * scale).dp
            val scaledHeight = ceil(srcSize.height.value * scale).dp

            return DpSize(scaledWidth, scaledHeight)
        }
    }

    /**
     * Scales the source to match the largest destination side while preserving aspect ratio.
     */
    object Fit : ImageScale {
        override fun scaleDp(srcSize: DpSize, dstSize: DpSize): DpSize {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)

            val scale = minOf(dstSize.width / srcSize.width, dstSize.height / srcSize.height)

            val scaledWidth = ceil(srcSize.width.value * scale).dp
            val scaledHeight = ceil(srcSize.height.value * scale).dp

            return DpSize(scaledWidth, scaledHeight)
        }
    }

    /**
     * Scales the source to match the destination width while preserving aspect ratio.
     */
    object FitWidth : ImageScale {
        override fun scaleDp(srcSize: DpSize, dstSize: DpSize): DpSize {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)

            val scale = dstSize.width / srcSize.width

            val scaledWidth = ceil(srcSize.width.value * scale).dp
            val scaledHeight = ceil(srcSize.height.value * scale).dp

            return DpSize(scaledWidth, scaledHeight)
        }
    }

    /**
     * Scales the source to match the destination height while preserving aspect ratio.
     */
    object FitHeight : ImageScale {
        override fun scaleDp(srcSize: DpSize, dstSize: DpSize): DpSize {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)

            val scale = dstSize.height / srcSize.height

            val scaledWidth = ceil(srcSize.width.value * scale).dp
            val scaledHeight = ceil(srcSize.height.value * scale).dp

            return DpSize(scaledWidth, scaledHeight)
        }
    }
}