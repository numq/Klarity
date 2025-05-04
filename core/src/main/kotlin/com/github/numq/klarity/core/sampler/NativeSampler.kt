package com.github.numq.klarity.core.sampler

import com.github.numq.klarity.core.cleaner.NativeCleaner
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
        external fun play(handle: Long, buffer: Long, size: Int)

        @JvmStatic
        external fun pause(handle: Long)

        @JvmStatic
        external fun stop(handle: Long)

        @JvmStatic
        external fun delete(handle: Long)
    }

    private val lock = Any()

    private val nativeHandle = AtomicLong(-1L)

    private fun ensureOpen() {
        check(nativeHandle.get() != -1L) { "Native sampler is closed" }
    }

    init {
        synchronized(lock) {
            require(sampleRate > 0) { "Invalid sample rate" }

            require(channels > 0) { "Invalid channels" }

            nativeHandle.set(Native.create(sampleRate = sampleRate, channels = channels))

            require(nativeHandle.get() != -1L) { "Could not instantiate native sampler" }
        }
    }

    private val cleanable = NativeCleaner.cleaner.register(this) {
        synchronized(lock) {
            ensureOpen()

            Native.delete(handle = nativeHandle.get())

            nativeHandle.set(-1L)
        }
    }

    fun setPlaybackSpeed(factor: Float) = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.setPlaybackSpeed(handle = nativeHandle.get(), factor = factor)
        }
    }

    fun setVolume(value: Float) = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.setVolume(handle = nativeHandle.get(), value = value)
        }
    }

    fun start() = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.start(handle = nativeHandle.get())
        }
    }

    fun play(buffer: Long, size: Int) = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.play(handle = nativeHandle.get(), buffer = buffer, size = size)
        }
    }

    fun pause() = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.pause(handle = nativeHandle.get())
        }
    }

    fun stop() = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.stop(handle = nativeHandle.get())
        }
    }

    override fun close() = cleanable.clean()
}