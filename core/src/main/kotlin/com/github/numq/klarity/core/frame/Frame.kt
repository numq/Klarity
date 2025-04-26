package com.github.numq.klarity.core.frame

import com.github.numq.klarity.core.cleaner.NativeCleaner
import com.github.numq.klarity.core.memory.NativeMemory
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

sealed interface Frame : Closeable {
    sealed interface Content : Frame {
        val bufferHandle: Long

        val bufferSize: Int

        val timestamp: Duration

        val isClosed: Boolean

        data class Audio(
            override val bufferHandle: Long,
            override val bufferSize: Int,
            override val timestamp: Duration,
        ) : Content {
            private val handle = AtomicLong(bufferHandle)

            override val isClosed = handle.get() == -1L

            private val cleanable = NativeCleaner.cleaner.register(this) {
                if (!isClosed) {
                    NativeMemory.free(handle.get())

                    handle.set(-1L)
                }
            }

            override fun close() = cleanable.clean()
        }

        data class Video(
            override val bufferHandle: Long,
            override val bufferSize: Int,
            override val timestamp: Duration,
            val width: Int,
            val height: Int
        ) : Content {
            private val handle = AtomicLong(bufferHandle)

            override val isClosed = handle.get() == -1L

            private val cleanable = NativeCleaner.cleaner.register(this) {
                if (!isClosed) {
                    NativeMemory.free(handle.get())

                    handle.set(-1L)
                }
            }

            override fun close() = cleanable.clean()
        }
    }

    data object EndOfStream : Frame {
        override fun close() = Unit
    }
}