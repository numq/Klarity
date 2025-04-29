package com.github.numq.klarity.core.sampler

import com.github.numq.klarity.core.cleaner.NativeCleaner
import java.io.Closeable

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

    private var nativeHandle = -1L

    init {
        synchronized(lock) {
            nativeHandle = Native.create(sampleRate = sampleRate, channels = channels)

            require(nativeHandle != -1L) { "Could not instantiate native sampler" }
        }
    }

    private val cleanable = NativeCleaner.cleaner.register(this) {
        synchronized(lock) {
            runCatching {
                if (nativeHandle != -1L) {
                    Native.delete(handle = nativeHandle)

                    nativeHandle = -1L
                }
            }.getOrDefault(Unit)
        }
    }

    fun setPlaybackSpeed(factor: Float) = synchronized(lock) {
        runCatching {
            Native.setPlaybackSpeed(handle = nativeHandle, factor = factor)
        }
    }

    fun setVolume(value: Float) = synchronized(lock) {
        runCatching {
            Native.setVolume(handle = nativeHandle, value = value)
        }
    }

    fun start() = synchronized(lock) {
        runCatching {
            Native.start(handle = nativeHandle)
        }
    }

    fun play(buffer: Long, size: Int) = synchronized(lock) {
        runCatching {
            Native.play(handle = nativeHandle, buffer = buffer, size = size)
        }
    }

    fun pause() = synchronized(lock) {
        runCatching {
            Native.pause(handle = nativeHandle)
        }
    }

    fun stop() = synchronized(lock) {
        runCatching {
            Native.stop(handle = nativeHandle)
        }
    }

    override fun close() = cleanable.clean()
}