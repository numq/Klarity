package io.github.numq.klarity.settings

/**
 * A data class representing the settings for the media player.
 *
 * @property playbackSpeedFactor factor by which to speed up or slow down playback
 * @property isMuted indicates whether the audio is muted
 * @property volume volume level of the audio (0.0 to 1.0)
 */
data class PlayerSettings(
    val playbackSpeedFactor: Float,
    val isMuted: Boolean,
    val volume: Float
)