package settings

data class PlayerSettings(
    val playbackSpeedFactor: Float,
    val isMuted: Boolean,
    val volume: Float,
    val audioBufferSize: Int,
    val videoBufferSize: Int,
    val seekOnlyKeyframes: Boolean,
)