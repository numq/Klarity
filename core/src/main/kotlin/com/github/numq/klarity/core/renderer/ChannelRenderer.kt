package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

internal class ChannelRenderer(override val format: VideoFormat) : Renderer {
    private val _frame = Channel<Frame.Content.Video>(Channel.CONFLATED, onUndeliveredElement = { frame ->
        frame.close()
    })

    override val frame = _frame.receiveAsFlow()

    override fun render(frame: Frame.Content.Video) {
        runCatching {
            check(frame.width == format.width && frame.height == format.height) { "Invalid video frame dimensions" }

            _frame.trySend(frame).getOrThrow()
        }.onFailure {
            frame.close()
        }
    }

    override fun close() {
        _frame.close()
    }
}