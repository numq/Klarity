package com.github.numq.klarity.core.format

internal data class NativeFormat(
    val location: String,
    val durationMicros: Long,
    val sampleRate: Int,
    val channels: Int,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val hwDeviceType: Int,
    val videoBufferSize: Int,
)