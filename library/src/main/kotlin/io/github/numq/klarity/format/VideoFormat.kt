package io.github.numq.klarity.format

import io.github.numq.klarity.hwaccel.HardwareAcceleration

data class VideoFormat(
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val hardwareAcceleration: HardwareAcceleration,
    val bufferCapacity: Int
)