package playback

import media.Media

data class PlaybackState(
    val media: Media? = null,
    val bufferTimestampMillis: Long = 0L,
    val playbackTimestampMillis: Long = 0L,
    val volume: Float = 1f,
    val isMuted: Boolean = false,
)