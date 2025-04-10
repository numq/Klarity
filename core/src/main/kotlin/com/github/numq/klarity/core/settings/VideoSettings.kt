package com.github.numq.klarity.core.settings

import com.github.numq.klarity.core.hwaccel.HardwareAcceleration

/**
 * A data class representing video settings for the media being prepared.
 *
 * @property width Desired width.
 * @property height Desired height.
 * @property frameRate Desired frame rate.
 * @property hardwareAccelerationCandidates Hardware acceleration candidates.
 */
data class VideoSettings(
    val width: Int? = null,
    val height: Int? = null,
    val frameRate: Double? = null,
    val hardwareAccelerationCandidates: List<HardwareAcceleration> = emptyList(),
)