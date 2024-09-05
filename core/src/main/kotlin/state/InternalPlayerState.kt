package state

import loop.buffer.BufferLoop
import loop.playback.PlaybackLoop
import media.Media
import pipeline.Pipeline

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