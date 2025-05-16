package io.github.numq.klarity.sampler

import io.github.numq.klarity.cleaner.NativeCleaner
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

internal class NativeSampler(sampleRate: Int, channels: Int) : Closeable {
    private object Native {
        @JvmStatic
        external fun create(sampleRate: Int, channels: Int): Long

        @JvmStatic
        external fun setPlaybackSpeed(handle: Long, factor: Float)

        @JvmStatic
        external fun setVolume(handle: Long, value: Float)

        @JvmStatic
        external fun start(handle: Long): Long

        @JvmStatic
        external fun write(handle: Long, bytes: ByteArray)

        @JvmStatic
        external fun stop(handle: Long)

        @JvmStatic
        external fun flush(handle: Long)

        @JvmStatic
        external fun drain(handle: Long)

        @JvmStatic
        external fun delete(handle: Long)
    }

    private val nativeHandle = AtomicLong(-1L)

    private val cleanable = NativeCleaner.cleaner.register(this) {
        val handle = nativeHandle.get()

        if (handle != -1L && nativeHandle.compareAndSet(handle, -1L)) {
            Native.delete(handle = handle)
        }
    }

    private fun ensureOpen() {
        check(nativeHandle.get() != -1L) { "Native sampler is closed" }
    }

    init {
        require(sampleRate > 0) { "Invalid sample rate" }

        require(channels > 0) { "Invalid channels" }

        nativeHandle.set(Native.create(sampleRate = sampleRate, channels = channels))

        require(nativeHandle.get() != -1L) { "Could not instantiate native sampler" }
    }

    fun setPlaybackSpeed(factor: Float) = runCatching {
        ensureOpen()

        Native.setPlaybackSpeed(handle = nativeHandle.get(), factor = factor)
    }

    fun setVolume(value: Float) = runCatching {
        ensureOpen()

        Native.setVolume(handle = nativeHandle.get(), value = value)
    }

    fun start() = runCatching {
        ensureOpen()

        Native.start(handle = nativeHandle.get())
    }

    fun write(bytes: ByteArray) = runCatching {
        ensureOpen()

        Native.write(handle = nativeHandle.get(), bytes = bytes)
    }

    fun stop() = runCatching {
        ensureOpen()

        Native.stop(handle = nativeHandle.get())
    }

    fun flush() = runCatching {
        ensureOpen()

        Native.flush(handle = nativeHandle.get())
    }

    fun drain() = runCatching {
        ensureOpen()

        Native.drain(handle = nativeHandle.get())
    }

    override fun close() = cleanable.clean()
}