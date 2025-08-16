package io.github.numq.example.playback

import kotlin.time.Duration

sealed interface PlaybackState {
    data object Empty : PlaybackState

    data object Preparing : PlaybackState

    data object Releasing : PlaybackState

    sealed interface Ready : PlaybackState {
        val location: String

        val duration: Duration

        val hasVideo: Boolean

        val isMuted: Boolean

        val volume: Float

        val bufferTimestamp: Duration

        val playbackTimestamp: Duration

        val playbackSpeedFactor: Float

        data class Playing(
            override val location: String,
            override val duration: Duration,
            override val hasVideo: Boolean,
            override val isMuted: Boolean,
            override val volume: Float,
            override val bufferTimestamp: Duration,
            override val playbackTimestamp: Duration,
            override val playbackSpeedFactor: Float,
        ) : Ready

        data class Paused(
            override val location: String,
            override val duration: Duration,
            override val hasVideo: Boolean,
            override val isMuted: Boolean,
            override val volume: Float,
            override val bufferTimestamp: Duration,
            override val playbackTimestamp: Duration,
            override val playbackSpeedFactor: Float,
        ) : Ready

        data class Stopped(
            override val location: String,
            override val duration: Duration,
            override val hasVideo: Boolean,
            override val isMuted: Boolean,
            override val volume: Float,
            override val bufferTimestamp: Duration,
            override val playbackTimestamp: Duration,
            override val playbackSpeedFactor: Float,
        ) : Ready

        data class Completed(
            override val location: String,
            override val duration: Duration,
            override val hasVideo: Boolean,
            override val isMuted: Boolean,
            override val volume: Float,
            override val bufferTimestamp: Duration,
            override val playbackTimestamp: Duration,
            override val playbackSpeedFactor: Float,
        ) : Ready

        data class Seeking(
            override val location: String,
            override val duration: Duration,
            override val hasVideo: Boolean,
            override val isMuted: Boolean,
            override val volume: Float,
            override val bufferTimestamp: Duration,
            override val playbackTimestamp: Duration,
            override val playbackSpeedFactor: Float,
        ) : Ready
    }

    data class Error(val exception: Exception) : PlaybackState
}