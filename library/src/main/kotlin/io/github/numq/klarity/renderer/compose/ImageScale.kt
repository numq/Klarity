package io.github.numq.klarity.renderer.compose

import androidx.compose.ui.geometry.Size

/**
 * An interface for defining image scaling operations.
 */
interface ImageScale {

    /**
     * Scales the source image dimensions to match the destination image dimensions.
     *
     * @param srcSize the dimensions of the source image
     * @param dstSize the dimensions of the destination image
     *
     * @return the scaled dimensions of the source image
     */
    fun scale(srcSize: Size, dstSize: Size): Size

    companion object {
        /**
         * Validates that source and destination dimensions are positive.
         *
         * @param srcSize the dimensions of the source image
         * @param dstSize the dimensions of the destination image
         *
         * @throws IllegalArgumentException if either source or destination dimensions are not positive
         */
        private fun validateDimensions(
            srcSize: Size,
            dstSize: Size,
        ) {
            require(srcSize.width > 0f && srcSize.height > 0f) { "Source dimensions must be positive" }
            require(dstSize.width > 0f && dstSize.height > 0f) { "Destination dimensions must be positive" }
        }

        /**
         * Scales the source to match the destination dimension (either width or height) while preserving aspect ratio.
         *
         * @param scaleFactor the factor by which to scale (based on width or height)
         *
         * @return the scaled dimensions of the source image
         */
        private fun scaleByFactor(srcSize: Size, scaleFactor: Float): Size {
            val scaledWidth = srcSize.width * scaleFactor
            val scaledHeight = srcSize.height * scaleFactor
            return Size(scaledWidth, scaledHeight)
        }
    }

    /**
     * No scaling. Returns the destination dimensions unchanged.
     */
    object None : ImageScale {
        override fun scale(srcSize: Size, dstSize: Size) = dstSize
    }

    /**
     * Scales the source uniformly to completely fill the destination.
     */
    object Crop : ImageScale {
        override fun scale(srcSize: Size, dstSize: Size): Size {
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
        override fun scale(srcSize: Size, dstSize: Size): Size {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)

            val scaledWidth = dstSize.width
            val scaledHeight = dstSize.height

            return Size(scaledWidth, scaledHeight)
        }
    }

    /**
     * Scales the source to match the largest destination side while preserving aspect ratio.
     */
    object Fit : ImageScale {
        override fun scale(srcSize: Size, dstSize: Size): Size {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)

            val scale = minOf(dstSize.width / srcSize.width, dstSize.height / srcSize.height)

            return scaleByFactor(srcSize, scale)
        }
    }

    /**
     * Scales the source to match the destination width while preserving aspect ratio.
     */
    object FitWidth : ImageScale {
        override fun scale(srcSize: Size, dstSize: Size): Size {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)
            val scale = dstSize.width / srcSize.width
            return scaleByFactor(srcSize, scale)
        }
    }

    /**
     * Scales the source to match the destination height while preserving aspect ratio.
     */
    object FitHeight : ImageScale {
        override fun scale(srcSize: Size, dstSize: Size): Size {
            validateDimensions(srcSize = srcSize, dstSize = dstSize)
            val scale = dstSize.height / srcSize.height
            return scaleByFactor(srcSize, scale)
        }
    }
}