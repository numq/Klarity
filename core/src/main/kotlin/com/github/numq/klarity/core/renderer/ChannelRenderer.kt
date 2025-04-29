package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.receiveAsFlow

internal class ChannelRenderer(override val format: VideoFormat) : Renderer {
    private val _frame = Channel<Frame.Content.Video>(Channel.CONFLATED)

    override val frame = _frame.receiveAsFlow()

    override fun render(frame: Frame.Content.Video) {
        if (frame.width == format.width && frame.height == format.height) {
            _frame.trySend(frame).getOrThrow()
        }
    }

    override fun close() {
        _frame.close()
    }
}