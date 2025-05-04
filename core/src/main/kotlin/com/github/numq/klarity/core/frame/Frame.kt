package com.github.numq.klarity.core.frame

import com.github.numq.klarity.core.data.Data
import kotlin.time.Duration

sealed interface Frame {
    sealed interface Content : Frame {
        val data: Data

        val size: Int

        val timestamp: Duration

        val isClosed: () -> Boolean

        data class Audio(
            override val data: Data,
            override val size: Int,
            override val timestamp: Duration,
            override val isClosed: () -> Boolean
        ) : Content

        data class Video(
            override val data: Data,
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