package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn

internal class DefaultRenderer(
    override val format: VideoFormat,
    override val preview: Frame.Video.Content?,
) : Renderer {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val _frame = Channel<Frame.Video.Content?>(Channel.CONFLATED)

    override val frame = _frame.receiveAsFlow().stateIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly,
        initialValue = preview
    )

    override var playbackSpeedFactor = MutableStateFlow(1f)

    override suspend fun setPlaybackSpeed(factor: Float) = runCatching {
        require(factor > 0f) { "Speed factor should be positive" }

        playbackSpeedFactor.value = factor
    }

    override suspend fun draw(frame: Frame.Video.Content) = runCatching {
        _frame.send(frame)
    }

    override suspend fun reset() = runCatching {
        _frame.send(preview)
    }

    override suspend fun close() = runCatching {
        coroutineScope.cancel()

        _frame.close()

        Unit
    }
}