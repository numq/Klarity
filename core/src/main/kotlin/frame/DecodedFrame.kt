package frame

data class DecodedFrame(
    val type: Type,
    val timestampMicros: Long,
    val bytes: ByteArray,
) {
    enum class Type {
        AUDIO, VIDEO
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as DecodedFrame

        if (type != other.type) return false
        if (timestampMicros != other.timestampMicros) return false
        return bytes.contentEquals(other.bytes)
    }

    override fun hashCode(): Int {
        var result = type.hashCode()
        result = 31 * result + timestampMicros.hashCode()
        result = 31 * result + bytes.contentHashCode()
        return result
    }
}