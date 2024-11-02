package com.github.numq.klarity.compose.scale

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp

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

        /**
         * Scales the source to match the destination dimension (either width or height) while preserving aspect ratio.
         *
         * @param scaleFactor The factor by which to scale (based on width or height).
         * @return The scaled dimensions of the source image.
         */
        private fun scaleByFactor(srcSize: DpSize, scaleFactor: Float): DpSize {
            val scaledWidth = (srcSize.width.value * scaleFactor).dp
            val scaledHeight = (srcSize.height.value * scaleFactor).dp
            return DpSize(scaledWidth, scaledHeight)
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

            val scaleWidth = dstSize.width / srcSize.width
            val scaleHeight = dstSize.height / srcSize.height

            val scale = maxOf(scaleWidth, scaleHeight)

            return scaleByFactor(srcSize, scale)
        }
    }

    /**
     * Scales the source non-uniformly to completely fill the destination.
     */
    object Fill : ImageScale {
        override fun scaleDp(srcSize: DpSize, dstSize: DpSize): DpSize {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)

            val scaledWidth = dstSize.width
            val scaledHeight = dstSize.height

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

            val scaledWidth = (srcSize.width.value * scale).dp
            val scaledHeight = (srcSize.height.value * scale).dp

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
            return scaleByFactor(srcSize, scale)
        }
    }

    /**
     * Scales the source to match the destination height while preserving aspect ratio.
     */
    object FitHeight : ImageScale {
        override fun scaleDp(srcSize: DpSize, dstSize: DpSize): DpSize {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)
            val scale = dstSize.height / srcSize.height
            return scaleByFactor(srcSize, scale)
        }
    }
}