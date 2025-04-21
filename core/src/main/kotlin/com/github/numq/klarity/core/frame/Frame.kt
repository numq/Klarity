package com.github.numq.klarity.core.frame

import kotlin.time.Duration

sealed interface Frame {
    sealed interface Audio : Frame {
        data class Content(
            val timestamp: Duration,
            val bytes: ByteArray,
        ) : Audio {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Content

                if (timestamp != other.timestamp) return false
                if (!bytes.contentEquals(other.bytes)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = timestamp.hashCode()
                result = 31 * result + bytes.contentHashCode()
                return result
            }
        }

        data object EndOfStream : Audio
    }

    sealed interface Video : Frame {
        data class Content(
            val timestamp: Duration,
            val bytes: ByteArray,
            val width: Int,
            val height: Int,
        ) : Video {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Content

                if (width != other.width) return false
                if (height != other.height) return false
                if (timestamp != other.timestamp) return false
                if (!bytes.contentEquals(other.bytes)) return false

                return true
            }

            override fun hashCode(): Int {
                var result = width
                result = 31 * result + height
                result = 31 * result + timestamp.hashCode()
                result = 31 * result + bytes.contentHashCode()
                return result
            }
        }

        data object EndOfStream : Video
    }
}