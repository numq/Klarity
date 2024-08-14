package settings

data class Settings(
    val playbackSpeedFactor: Float,
    val isMuted: Boolean,
    val volume: Float,
    val audioBufferSize: Int,
    val videoBufferSize: Int,
)