package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.renderer.Renderer
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.ImageInfo
import org.jetbrains.skia.Surface

interface SkiaRenderer : Renderer {
    val generationId: StateFlow<Int>

    fun drawsNothing(): Boolean

    fun draw(callback: (Surface) -> Unit)

    suspend fun withCache(callback: suspend (List<CachedFrame>) -> Unit)

    suspend fun render(cachedFrame: CachedFrame): Result<Unit>

    companion object {
        fun create(format: VideoFormat): Result<SkiaRenderer> = runCatching {
            val imageInfo = ImageInfo(
                width = format.width,
                height = format.height,
                colorType = ColorType.BGRA_8888,
                alphaType = ColorAlphaType.UNPREMUL
            )

            val surface = Surface.makeRaster(imageInfo)

            DefaultSkiaRenderer(format = format, imageInfo = imageInfo, surface = surface)
        }
    }
}