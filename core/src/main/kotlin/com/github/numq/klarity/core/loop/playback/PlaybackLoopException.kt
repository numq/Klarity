package com.github.numq.klarity.core.loop.playback

data class PlaybackLoopException(override val cause: Throwable) : Exception(cause)