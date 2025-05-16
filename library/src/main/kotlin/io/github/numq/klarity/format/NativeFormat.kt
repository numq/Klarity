package io.github.numq.klarity.format

internal data class NativeFormat(
    val location: String,
    val durationMicros: Long,
    val sampleRate: Int,
    val channels: Int,
    val width: Int,
    val height: Int,
    val frameRate: Double,
    val hwDeviceType: Int,
    val videoBufferCapacity: Int
)