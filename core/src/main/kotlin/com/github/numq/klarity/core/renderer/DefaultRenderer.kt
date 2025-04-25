package com.github.numq.klarity.core.renderer

import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import java.util.concurrent.atomic.AtomicReference

internal class DefaultRenderer(override val format: VideoFormat) : Renderer {
    private fun isFormatValid(frame: Frame.Video.Content) = frame.width == format.width && frame.height == format.height

    private val callbackRef = AtomicReference<(Frame.Video.Content) -> Unit?>(null)

    override fun onRender(callback: (Frame.Video.Content) -> Unit) {
        callbackRef.set(callback)
    }

    override fun render(frame: Frame.Video.Content) {
        frame.use {
            if (isFormatValid(frame)) {
                callbackRef.get()?.invoke(frame)
            }
        }
    }

    override fun close() {
        callbackRef.set(null)
    }
}