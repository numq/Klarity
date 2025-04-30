package com.github.numq.klarity.core.frame

import kotlin.time.Duration

sealed interface Frame {
    sealed interface Content : Frame {
        val buffer: Long

        val size: Int

        val timestamp: Duration

        val isClosed: () -> Boolean

        data class Audio(
            override val buffer: Long,
            override val size: Int,
            override val timestamp: Duration,
            override val isClosed: () -> Boolean
        ) : Content

        data class Video(
            override val buffer: Long,
            override val size: Int,
            override val timestamp: Duration,
            override val isClosed: () -> Boolean,
            val width: Int,
            val height: Int,
            val onRenderStart: (() -> Unit)? = null,
            val onRenderComplete: ((renderTime: Duration) -> Unit)? = null
        ) : Content
    }

    data object EndOfStream : Frame
}