package com.github.numq.klarity.core.state

import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.loop.playback.PlaybackLoop
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline

internal sealed interface InternalPlayerState {
    data object Empty : InternalPlayerState

    data object Preparing : InternalPlayerState

    data class Ready(
        val media: Media,
        val pipeline: Pipeline,
        val bufferLoop: BufferLoop,
        val playbackLoop: PlaybackLoop,
        val status: Status,
    ) : InternalPlayerState {
        enum class Status {
            TRANSITION, PLAYING, PAUSED, STOPPED, COMPLETED, SEEKING, RELEASING
        }

        internal fun updateStatus(status: Status) = copy(status = status)
    }
}