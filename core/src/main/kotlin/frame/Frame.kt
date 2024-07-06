package frame

sealed interface Frame {
    val timestampMicros: Long

    sealed interface Audio : Frame {
        data class Content(
            override val timestampMicros: Long,
            val bytes: ByteArray,
            val channels: Int,
            val sampleRate: Int,
        ) : Audio {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Content

                if (timestampMicros != other.timestampMicros) return false
                if (!bytes.contentEquals(other.bytes)) return false
                if (channels != other.channels) return false
                if (sampleRate != other.sampleRate) return false

                return true
            }

            override fun hashCode(): Int {
                var result = timestampMicros.hashCode()
                result = 31 * result + bytes.contentHashCode()
                result = 31 * result + channels
                result = 31 * result + sampleRate
                return result
            }
        }

        data class EndOfMedia(override val timestampMicros: Long) : Audio
    }

    sealed interface Video : Frame {
        data class Content(
            override val timestampMicros: Long,
            val bytes: ByteArray,
            val width: Int,
            val height: Int,
            val frameRate: Double,
        ) : Video {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Content

                if (timestampMicros != other.timestampMicros) return false
                if (!bytes.contentEquals(other.bytes)) return false
                if (width != other.width) return false
                if (height != other.height) return false
                if (frameRate != other.frameRate) return false

                return true
            }

            override fun hashCode(): Int {
                var result = timestampMicros.hashCode()
                result = 31 * result + bytes.contentHashCode()
                result = 31 * result + width
                result = 31 * result + height
                result = 31 * result + frameRate.hashCode()
                return result
            }

        }

        data class EndOfMedia(override val timestampMicros: Long) : Video
    }
}