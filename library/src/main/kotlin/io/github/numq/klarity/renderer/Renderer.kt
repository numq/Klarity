package io.github.numq.klarity.renderer

import io.github.numq.klarity.format.VideoFormat
import io.github.numq.klarity.frame.Frame

interface Renderer {
    val format: VideoFormat

    suspend fun render(frame: Frame.Content.Video): Result<Unit>

    suspend fun save(frame: Frame.Content.Video): Result<Unit>

    suspend fun close(): Result<Unit>
}