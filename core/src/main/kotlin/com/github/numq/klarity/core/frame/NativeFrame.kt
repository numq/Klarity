package com.github.numq.klarity.core.frame

internal data class NativeFrame(
    val bufferHandle: Long,
    val bufferSize: Int,
    val timestampMicros: Long,
    val type: Int
) {
    internal enum class Type {
        AUDIO, VIDEO
    }

    fun getType() = Type.entries.getOrNull(type)
}