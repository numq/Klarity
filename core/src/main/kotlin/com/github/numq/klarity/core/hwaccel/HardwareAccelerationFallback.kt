package com.github.numq.klarity.core.hwaccel

data class HardwareAccelerationFallback(
    val candidates: List<HardwareAcceleration> = emptyList(),
    val useSoftwareAcceleration: Boolean = true,
)