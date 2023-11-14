package player

import media.Media

data class PlayerState(
    val media: Media? = null,
    val bufferTimestampMillis: Long = 0L,
    val playbackTimestampMillis: Long = 0L,
    val loopCount: Int = 0,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
)