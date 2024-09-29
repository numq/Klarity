package settings

/**
 * Data class representing the settings for the media player.
 *
 * @property playbackSpeedFactor Factor by which to speed up or slow down playback.
 * @property isMuted Indicates whether the audio is muted.
 * @property volume Volume level of the audio (0.0 to 1.0).
 * @property audioBufferSize Size of the audio buffer in bytes.
 * @property videoBufferSize Size of the video buffer in bytes.
 * @property seekOnlyKeyframes Indicates whether seeking is allowed only at keyframes.
 */
data class PlayerSettings(
    val playbackSpeedFactor: Float,
    val isMuted: Boolean,
    val volume: Float,
    val audioBufferSize: Int,
    val videoBufferSize: Int,
    val seekOnlyKeyframes: Boolean,
)