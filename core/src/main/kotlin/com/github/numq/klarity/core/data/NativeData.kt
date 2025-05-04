package com.github.numq.klarity.core.data

import com.github.numq.klarity.core.cleaner.NativeCleaner
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

internal data class NativeData(val pointer: Long, val capacity: Int) : Closeable {
    private object Native {
        @JvmStatic
        external fun allocate(capacity: Int): Long

        @JvmStatic
        external fun free(pointer: Long)
    }

    companion object {
        fun allocate(capacity: Int) = NativeData(pointer = Native.allocate(capacity = capacity), capacity = capacity)
    }

    private val lock = Any()

    private val nativeHandle = AtomicLong(-1L)

    private fun ensureOpen() {
        check(nativeHandle.get() != -1L) { "Native data is closed" }
    }

    init {
        synchronized(lock) {
            nativeHandle.set(Native.allocate(capacity = capacity))

            require(nativeHandle.get() != -1L) { "Could not instantiate native data" }
        }
    }

    private val cleanable = NativeCleaner.cleaner.register(this) {
        synchronized(lock) {
            ensureOpen()

            Native.free(pointer = nativeHandle.get())

            nativeHandle.set(-1L)
        }
    }

    fun isClosed() = nativeHandle.get() == -1L

    override fun close() = cleanable.clean()
}