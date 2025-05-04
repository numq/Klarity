package com.github.numq.klarity.core.format

data class AudioFormat(
    val sampleRate: Int,
    val channels: Int,
    val bufferCapacity: Int
)