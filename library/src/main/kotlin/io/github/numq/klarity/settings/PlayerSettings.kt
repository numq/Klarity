package io.github.numq.klarity.settings

/**
 * A data class representing the settings for the media player.
 *
 * @property playbackSpeedFactor factor by which to speed up or slow down playback
 * @property volume volume level of the audio (0.0 to 1.0)
 * @property isMuted indicates whether the audio is muted
 */
data class PlayerSettings(
    val playbackSpeedFactor: Float,
    val volume: Float,
    val isMuted: Boolean
) {
    companion object {
        val DEFAULT = PlayerSettings(playbackSpeedFactor = 1f, volume = 1f, isMuted = false)
    }
}