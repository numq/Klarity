package com.github.numq.klarity.core.settings

/**
 * A data class representing the settings for the media player.
 *
 * @property playbackSpeedFactor Factor by which to speed up or slow down playback.
 * @property isMuted Indicates whether the audio is muted.
 * @property volume Volume level of the audio (0.0 to 1.0).
 * @property audioBufferSize Size of the audio buffer in frames.
 * @property videoBufferSize Size of the video buffer in frames.
 */
data class PlayerSettings(
    val playbackSpeedFactor: Float,
    val isMuted: Boolean,
    val volume: Float,
    val audioBufferSize: Int,
    val videoBufferSize: Int
)