package com.github.numq.klarity.core.frame

import com.github.numq.klarity.core.data.Data
import kotlin.time.Duration

sealed interface Frame {
    sealed interface Content : Frame {
        val timestamp: Duration

        data class Audio(
            val bytes: ByteArray,
            override val timestamp: Duration
        ) : Content {
            override fun equals(other: Any?): Boolean {
                if (this === other) return true
                if (javaClass != other?.javaClass) return false

                other as Audio

                if (!bytes.contentEquals(other.bytes)) return false
                if (timestamp != other.timestamp) return false

                return true
            }

            override fun hashCode(): Int {
                var result = bytes.contentHashCode()
                result = 31 * result + timestamp.hashCode()
                return result
            }
        }

        data class Video(
            val data: Data,
            val size: Int,
            override val timestamp: Duration,
            val isClosed: () -> Boolean,
            val width: Int,
            val height: Int,
            val onRenderStart: (() -> Unit)? = null,
            val onRenderComplete: ((renderTime: Duration) -> Unit)? = null
        ) : Content
    }

    data object EndOfStream : Frame
}