package com.github.numq.klarity.compose.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.renderer.Renderer
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.skia.Color
import org.jetbrains.skia.Surface

interface SkiaRenderer : Renderer {
    val generationId: StateFlow<Int>

    fun drawsNothing(): Boolean

    fun draw(callback: (Surface) -> Unit)

    suspend fun withCache(callback: suspend (List<CachedFrame>) -> Unit)

    suspend fun render(cachedFrame: CachedFrame): Result<Unit>

    suspend fun flush(color: Int = Color.BLACK): Result<Unit>

    companion object {
        fun create(format: VideoFormat): Result<SkiaRenderer> = runCatching {
            DefaultSkiaRenderer(format = format)
        }
    }
}