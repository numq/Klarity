package frame

sealed class Frame private constructor(open val micros: Long, open val bytes: ByteArray) {
    data class Audio(
        override val micros: Long,
        override val bytes: ByteArray,
        val channels: Int,
        val sampleRate: Int,
    ) : Frame(micros = micros, bytes = bytes) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Audio

            if (micros != other.micros) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (channels != other.channels) return false
            return sampleRate == other.sampleRate
        }

        override fun hashCode(): Int {
            var result = micros.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + channels
            result = 31 * result + sampleRate
            return result
        }
    }

    data class Video(
        override val micros: Long,
        override val bytes: ByteArray,
        val width: Int,
        val height: Int,
    ) : Frame(micros = micros, bytes = bytes) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as Video

            if (micros != other.micros) return false
            if (!bytes.contentEquals(other.bytes)) return false
            if (width != other.width) return false
            return height == other.height
        }

        override fun hashCode(): Int {
            var result = micros.hashCode()
            result = 31 * result + bytes.contentHashCode()
            result = 31 * result + width
            result = 31 * result + height
            return result
        }
    }
}