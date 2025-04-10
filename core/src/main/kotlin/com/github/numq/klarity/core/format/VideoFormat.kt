package com.github.numq.klarity.core.format

import com.github.numq.klarity.core.hwaccel.HardwareAcceleration

data class VideoFormat(
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val hardwareAcceleration: HardwareAcceleration,
)