package com.github.numq.klarity.core.frame

internal data class NativeAudioFrame(
    val timestampMicros: Long,
    val audioBytes: ByteArray,
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NativeAudioFrame

        if (timestampMicros != other.timestampMicros) return false
        if (!audioBytes.contentEquals(other.audioBytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = timestampMicros.hashCode()
        result = 31 * result + audioBytes.contentHashCode()
        return result
    }
}