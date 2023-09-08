package decoder

sealed class DecodedFrame private constructor(
    open val timestampNanos: Long,
) {
    data class End(override val timestampNanos: Long) : DecodedFrame(timestampNanos)

    data class Video(
        override val timestampNanos: Long,
        val bytes: ByteArray,
    ) : DecodedFrame(timestampNanos) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Video

            if (timestampNanos != other.timestampNanos) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = timestampNanos.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    data class Audio(
        override val timestampNanos: Long,
        val bytes: ByteArray,
    ) : DecodedFrame(timestampNanos) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Audio

            if (timestampNanos != other.timestampNanos) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = timestampNanos.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }
}