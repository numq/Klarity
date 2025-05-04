package com.github.numq.klarity.core.data

import com.github.numq.klarity.core.cleaner.NativeCleaner
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

internal data class NativeData(val capacity: Int) : Closeable {
    private object Native {
        @JvmStatic
        external fun allocate(capacity: Int): Long

        @JvmStatic
        external fun free(handle: Long)
    }

    companion object {
        fun allocate(capacity: Int) = NativeData(capacity = capacity)
    }

    private val nativeHandle = AtomicLong(-1L)

    private val cleanable = NativeCleaner.cleaner.register(this) {
        val handle = nativeHandle.getAndSet(-1L)

        if (handle != -1L) {
            Native.free(handle)
        }
    }

    private fun ensureOpen() {
        check(nativeHandle.get() != -1L) { "Native data is closed" }
    }

    init {
        nativeHandle.set(Native.allocate(capacity = capacity))

        require(nativeHandle.get() != -1L) { "Could not instantiate native data" }
    }

    fun getBuffer(): Long {
        ensureOpen()

        return nativeHandle.get()
    }

    fun isClosed() = nativeHandle.get() == -1L

    override fun close() = cleanable.clean()
}