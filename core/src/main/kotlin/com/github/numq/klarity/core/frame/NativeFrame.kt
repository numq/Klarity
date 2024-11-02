package com.github.numq.klarity.core.frame

internal data class NativeFrame(val type: Int, val timestampMicros: Long, val bytes: ByteArray) {
    enum class Type {
        AUDIO, VIDEO
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as NativeFrame

        if (type != other.type) return false
        if (timestampMicros != other.timestampMicros) return false
        if (!bytes.contentEquals(other.bytes)) return false

        return true
    }

    override fun hashCode(): Int {
        var result = type
        result = 31 * result + timestampMicros.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}
