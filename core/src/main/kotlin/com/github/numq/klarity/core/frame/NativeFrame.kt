package com.github.numq.klarity.core.frame

internal data class NativeFrame(
    val bufferHandle: Long,
    val bufferSize: Int,
    val timestampMicros: Long
)