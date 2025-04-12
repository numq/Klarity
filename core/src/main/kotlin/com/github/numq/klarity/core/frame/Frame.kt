package com.github.numq.klarity.core.frame

sealed interface Frame {
    sealed interface Audio : Frame {
        data class Content(
            val timestampMicros: Long,
            val bytes: ByteArray,
        ) : Audio {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Content

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

        data object EndOfStream : Audio
    }

    sealed interface Video : Frame {
        data class Content(
            val timestampMicros: Long,
            val bytes: ByteArray,
            val width: Int,
            val height: Int,
        ) : Video {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Content

                if (timestampMicros != other.timestampMicros) return false
                if (!bytes.contentEquals(other.bytes)) return false
                if (width != other.width) return false
                if (height != other.height) return false

                return true
            }

            override fun hashCode(): Int {
                var result = timestampMicros.hashCode()
                result = 31 * result + bytes.contentHashCode()
                result = 31 * result + width
                result = 31 * result + height
                return result
            }
        }

        data object EndOfStream : Video
    }
}