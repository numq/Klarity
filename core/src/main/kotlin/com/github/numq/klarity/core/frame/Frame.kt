package com.github.numq.klarity.core.frame

import java.io.Closeable
import kotlin.time.Duration

sealed interface Frame {
    sealed interface Content : Frame, Closeable {
        val buffer: Long

        val size: Int

        val timestamp: Duration

        val isClosed: () -> Boolean

        val onClose: () -> Unit

        data class Audio(
            override val buffer: Long,
            override val size: Int,
            override val timestamp: Duration,
            override val isClosed: () -> Boolean,
            override val onClose: () -> Unit
        ) : Content {
            override fun close() {
                // todo

                onClose()
            }
        }

        data class Video(
            override val buffer: Long,
            override val size: Int,
            override val timestamp: Duration,
            override val isClosed: () -> Boolean,
            override val onClose: () -> Unit,
            val width: Int,
            val height: Int,
            val onRenderStart: (() -> Unit)? = null,
            val onRenderComplete: ((renderTime: Duration) -> Unit)? = null
        ) : Content {
            override fun close() {
                // todo

                onClose()
            }
        }
    }

    data object EndOfStream : Frame
}