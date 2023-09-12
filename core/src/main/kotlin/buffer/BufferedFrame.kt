package buffer

sealed class BufferedFrame private constructor() {
    data class Samples(val timestampNanos: Long, val bytes: ByteArray) : BufferedFrame() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Samples

            if (timestampNanos != other.timestampNanos) return false
            return bytes.contentEquals(other.bytes)
        }

        override fun hashCode(): Int {
            var result = timestampNanos.hashCode()
            result = 31 * result + bytes.contentHashCode()
            return result
        }
    }

    data class Pixels(val timestampNanos: Long, val bytes: ByteArray) : BufferedFrame() {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Pixels

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