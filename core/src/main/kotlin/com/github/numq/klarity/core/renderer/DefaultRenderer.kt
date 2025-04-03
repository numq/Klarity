package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn

internal class DefaultRenderer(
    override val width: Int,
    override val height: Int,
    override val frameRate: Double,
    override val preview: Frame.Video.Content?,
) : Renderer {
    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    private val coroutineScope = CoroutineScope(coroutineContext)

    private var buffer = Channel<Frame.Video.Content?>(2)

    override val frame = buffer.consumeAsFlow().stateIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily,
        initialValue = preview
    )

    override var playbackSpeedFactor = MutableStateFlow(1f)

    override suspend fun setPlaybackSpeed(factor: Float) = runCatching {
        require(factor > 0f) { "Speed factor should be positive" }

        playbackSpeedFactor.emit(factor)
    }

    override suspend fun draw(frame: Frame.Video.Content) = runCatching {
        this@DefaultRenderer.buffer.send(frame)
    }

    override suspend fun reset() = runCatching {
        buffer.send(preview)
    }

    override suspend fun close() = runCatching {
        coroutineScope.cancel()

        buffer.close()

        Unit
    }
}