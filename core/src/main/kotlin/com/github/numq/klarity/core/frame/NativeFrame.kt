package com.github.numq.klarity.core.frame

internal data class NativeAudioFrame(
    val bytes: ByteArray,
    val timestampMicros: Long
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NativeAudioFrame

        if (timestampMicros != other.timestampMicros) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestampMicros.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}

internal data class NativeVideoFrame(
    val remaining: Int,
    val timestampMicros: Long
)