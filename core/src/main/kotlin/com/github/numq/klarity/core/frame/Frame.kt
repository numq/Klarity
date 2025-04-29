package com.github.numq.klarity.core.frame

import kotlin.time.Duration

sealed interface Frame {
    sealed interface Content : Frame {
        val buffer: Long

        val size: Int

        val timestamp: Duration

        data class Audio(
            override val buffer: Long,
            override val size: Int,
            override val timestamp: Duration
        ) : Content

        data class Video(
            override val buffer: Long,
            override val size: Int,
            override val timestamp: Duration,
            val width: Int,
            val height: Int,
            val onRenderStart: (() -> Unit)? = null,
            val onRenderComplete: ((renderTime: Duration) -> Unit)? = null
        ) : Content
    }

    data object EndOfStream : Frame
}