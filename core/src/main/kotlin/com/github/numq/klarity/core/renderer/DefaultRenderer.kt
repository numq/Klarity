package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.flow.MutableStateFlow

internal class DefaultRenderer(
    override val format: VideoFormat,
    override val preview: Frame.Video.Content?,
) : Renderer {
    override val frame = MutableStateFlow(value = preview)

    override var playbackSpeedFactor = MutableStateFlow(1f)

    override suspend fun setPlaybackSpeed(factor: Float) = runCatching {
        require(factor > 0f) { "Speed factor should be positive" }

        playbackSpeedFactor.emit(factor)
    }

    override suspend fun draw(frame: Frame.Video.Content) = runCatching {
        this@DefaultRenderer.frame.value = frame
    }

    override suspend fun reset() = runCatching {
        frame.value = preview
    }

    override suspend fun close() = runCatching {
        frame.value = null
    }
}