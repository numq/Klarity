package io.github.numq.klarity.loop.playback

data class PlaybackLoopException(override val cause: Throwable) : Exception(cause)