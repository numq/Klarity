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

    private var nativeHandle = AtomicLong(-1L)

    init {
        synchronized(lock) {
            nativeHandle.set(Native.create(sampleRate = sampleRate, channels = channels))

            require(nativeHandle.get() != -1L) { "Could not instantiate native sampler" }
        }
    }

    private val cleanable = NativeCleaner.cleaner.register(this) {
        synchronized(lock) {
            runCatching {
                if (nativeHandle.get() != -1L) {
                    Native.delete(handle = nativeHandle.get())

                    nativeHandle.set(-1L)
                }
            }.getOrDefault(Unit)
        }
    }

    fun setPlaybackSpeed(factor: Float) = synchronized(lock) {
        runCatching {
            Native.setPlaybackSpeed(handle = nativeHandle.get(), factor = factor)
        }
    }

    fun setVolume(value: Float) = synchronized(lock) {
        runCatching {
            Native.setVolume(handle = nativeHandle.get(), value = value)
        }
    }

    fun start() = synchronized(lock) {
        runCatching {
            Native.start(handle = nativeHandle.get())
        }
    }

    fun play(buffer: Long, size: Int) = synchronized(lock) {
        runCatching {
            Native.play(handle = nativeHandle.get(), buffer = buffer, size = size)
        }
    }

    fun pause() = synchronized(lock) {
        runCatching {
            Native.pause(handle = nativeHandle.get())
        }
    }

    fun stop() = synchronized(lock) {
        runCatching {
            Native.stop(handle = nativeHandle.get())
        }
    }

    override fun close() = cleanable.clean()
}