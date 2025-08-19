package io.github.numq.klarity.format

import io.github.numq.klarity.hwaccel.HardwareAcceleration

sealed interface Format {
    data class Audio(val sampleRate: Int, val channels: Int) : Format

    data class Video(
        val width: Int,
        val height: Int,
        val frameRate: Double,
        val hardwareAcceleration: HardwareAcceleration,
        val bufferCapacity: Int
    ) : Format
}