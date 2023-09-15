package player

data class PlayerState(
    val volume: Double = 1.0,
    val isMuted: Boolean = false,
    val bufferTimestampMillis: Long = 0L,
    val playbackTimestampMillis: Long = 0L
)