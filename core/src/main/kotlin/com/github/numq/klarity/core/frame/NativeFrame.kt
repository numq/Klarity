package com.github.numq.klarity.core.frame

internal data class NativeFrame(
    val type: Int,
    val timestampMicros: Long,
) {
    enum class Type {
        AUDIO, VIDEO
    }
}