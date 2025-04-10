package com.github.numq.klarity.core.settings

/**
 * A data class representing audio settings for the media being prepared.
 *
 * @property sampleRate Desired sample rate.
 * @property channels Desired number of channels.
 */
data class AudioSettings(val sampleRate: Int? = null, val channels: Int? = null)