package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.onSubscription

internal class DefaultRenderer(
    override val format: VideoFormat,
    private val preview: Frame.Video.Content?,
) : Renderer {
    private val _frame = MutableSharedFlow<Frame.Video.Content>(
        replay = 1,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override val frame = _frame.asSharedFlow().onSubscription {
        if (preview != null) {
            emit(preview)
        }
    }

    override var playbackSpeedFactor = MutableStateFlow(1f)

    override suspend fun setPlaybackSpeed(factor: Float) = runCatching {
        require(factor > 0f) { "Speed factor should be positive" }

        playbackSpeedFactor.value = factor
    }

    override suspend fun draw(frame: Frame.Video.Content) = runCatching {
        _frame.tryEmit(frame)

        Unit
    }

    override suspend fun reset() = runCatching {
        preview?.let(_frame::tryEmit)

        Unit
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    override suspend fun close() = runCatching {
        _frame.resetReplayCache()
    }
}