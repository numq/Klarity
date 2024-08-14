package state

import loop.buffer.BufferLoop
import loop.playback.PlaybackLoop
import media.Media
import pipeline.Pipeline

internal sealed interface InternalState {
    data object Empty : InternalState

    sealed interface Loaded : InternalState {
        val media: Media
        val pipeline: Pipeline
        val bufferLoop: BufferLoop
        val playbackLoop: PlaybackLoop

        data class Playing(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
        ) : Loaded

        data class Paused(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
        ) : Loaded

        data class Stopped(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
        ) : Loaded

        data class Completed(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
        ) : Loaded

        data class Seeking(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
        ) : Loaded
    }
}