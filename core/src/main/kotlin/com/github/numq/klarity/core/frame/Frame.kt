package com.github.numq.klarity.core.frame

import com.github.numq.klarity.core.cleaner.NativeCleaner
import com.github.numq.klarity.core.memory.NativeMemory
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong
import kotlin.time.Duration

sealed interface Frame : Closeable {
    val isClosed: Boolean

    sealed interface Audio : Frame {
        data class Content(
            val bufferHandle: Long,
            val bufferSize: Int,
            val timestamp: Duration,
        ) : Audio {
            private val handle = AtomicLong(bufferHandle)

            private val cleanable = NativeCleaner.cleaner.register(this) {
                if (!isClosed) {
                    NativeMemory.free(handle.get())

                    handle.set(-1L)
                }
            }

            override val isClosed = handle.get() == -1L

            override fun close() = cleanable.clean()
        }

        data object EndOfStream : Audio {
            override val isClosed = true

            override fun close() = Unit
        }
    }

    sealed interface Video : Frame {
        data class Content(
            val bufferHandle: Long,
            val bufferSize: Int,
            val timestamp: Duration,
            val width: Int,
            val height: Int
        ) : Video {
            private val handle = AtomicLong(bufferHandle)

            private val cleanable = NativeCleaner.cleaner.register(this) {
                if (!isClosed) {
                    NativeMemory.free(handle.get())

                    handle.set(-1L)
                }
            }

            override val isClosed = handle.get() == -1L

            override fun close() = cleanable.clean()
        }

        data object EndOfStream : Video {
            override val isClosed = true

            override fun close() = Unit
        }
    }
}