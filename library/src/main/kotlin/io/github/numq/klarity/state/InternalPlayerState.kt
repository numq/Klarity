package io.github.numq.klarity.state

import io.github.numq.klarity.loop.buffer.BufferLoop
import io.github.numq.klarity.loop.playback.PlaybackLoop
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pipeline.Pipeline

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