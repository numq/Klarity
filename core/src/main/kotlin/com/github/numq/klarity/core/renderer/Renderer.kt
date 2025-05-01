package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame

interface Renderer {
    val format: VideoFormat

    suspend fun render(frame: Frame.Content.Video): Result<Unit>

    suspend fun save(frame: Frame.Content.Video): Result<Unit>

    suspend fun close(): Result<Unit>
}