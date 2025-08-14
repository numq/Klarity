package io.github.numq.klarity.state

import io.github.numq.klarity.loop.buffer.BufferLoop
import io.github.numq.klarity.loop.playback.PlaybackLoop
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pipeline.Pipeline

internal sealed interface InternalPlayerState {
    data object Empty : InternalPlayerState

    data object Preparing : InternalPlayerState

    data class Releasing(val previousState: Ready) : InternalPlayerState

    data class Error(val cause: Throwable, val previous: InternalPlayerState) : InternalPlayerState

    sealed interface Ready : InternalPlayerState {
        val media: Media

        val pipeline: Pipeline

        val bufferLoop: BufferLoop

        val playbackLoop: PlaybackLoop

        val previousState: Ready?

        private val destination: Destination
            get() = when (this) {
                is Playing -> Destination.PLAYING

                is Paused -> Destination.PAUSED

                is Stopped -> Destination.STOPPED

                is Completed -> Destination.COMPLETED

                is Seeking -> Destination.SEEKING

                is Transition -> error("Transition cannot be a destination")
            }

        fun Ready.startTransition(destination: Destination): Transition {
            require(destination != this.destination) { "Changing destination to the same is impossible" }

            return Transition(
                media = media,
                pipeline = pipeline,
                bufferLoop = bufferLoop,
                playbackLoop = playbackLoop,
                previousState = this,
                destination = destination
            )
        }

        fun completeTransition(state: Transition) = with(state) {
            when (destination) {
                Destination.PLAYING -> Playing(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                )

                Destination.PAUSED -> Paused(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                )

                Destination.STOPPED -> Stopped(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                )

                Destination.COMPLETED -> Completed(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                )

                Destination.SEEKING -> Seeking(
                    media = media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    previousState = previousState
                )
            }
        }

        data class Playing(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
            override val previousState: Ready,
        ) : Ready

        data class Paused(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
            override val previousState: Ready,
        ) : Ready

        data class Stopped(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
            override val previousState: Ready?,
        ) : Ready

        data class Completed(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
            override val previousState: Ready,
        ) : Ready

        data class Seeking(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
            override val previousState: Ready,
        ) : Ready

        data class Transition(
            override val media: Media,
            override val pipeline: Pipeline,
            override val bufferLoop: BufferLoop,
            override val playbackLoop: PlaybackLoop,
            override val previousState: Ready,
            val destination: Destination,
        ) : Ready
    }
}