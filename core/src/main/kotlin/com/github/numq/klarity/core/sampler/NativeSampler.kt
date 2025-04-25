package com.github.numq.klarity.core.sampler

import com.github.numq.klarity.core.cleaner.NativeCleaner
import java.io.Closeable

internal class NativeSampler(sampleRate: Int, channels: Int) : Closeable {
    companion object {
        @JvmStatic
        private external fun createNative(sampleRate: Int, channels: Int): Long

        @JvmStatic
        private external fun setPlaybackSpeedNative(handle: Long, factor: Float)

        @JvmStatic
        private external fun setVolumeNative(handle: Long, value: Float)

        @JvmStatic
        private external fun startNative(handle: Long): Long

        @JvmStatic
        private external fun playNative(handle: Long, bufferHandle: Long, bufferSize: Int)

        @JvmStatic
        private external fun pauseNative(handle: Long)

        @JvmStatic
        private external fun stopNative(handle: Long)

        @JvmStatic
        private external fun deleteNative(handle: Long)
    }

    private val lock = Any()

    private var nativeHandle = -1L

    init {
        synchronized(lock) {
            nativeHandle = createNative(sampleRate = sampleRate, channels = channels)

            require(nativeHandle != -1L) { "Could not instantiate native sampler" }
        }
    }

    private val cleanable = NativeCleaner.cleaner.register(this) {
        synchronized(lock) {
            runCatching {
                if (nativeHandle != -1L) {
                    deleteNative(handle = nativeHandle)

                    nativeHandle = -1L
                }
            }.getOrDefault(Unit)
        }
    }

    fun setPlaybackSpeed(factor: Float) = synchronized(lock) {
        runCatching {
            setPlaybackSpeedNative(handle = nativeHandle, factor = factor)
        }
    }

    fun setVolume(value: Float) = synchronized(lock) {
        runCatching {
            setVolumeNative(handle = nativeHandle, value = value)
        }
    }

    fun start() = synchronized(lock) {
        runCatching {
            startNative(handle = nativeHandle)
        }
    }

    fun play(bufferHandle: Long, bufferSize: Int) = synchronized(lock) {
        runCatching {
            playNative(handle = nativeHandle, bufferHandle = bufferHandle, bufferSize = bufferSize)
        }
    }

    fun pause() = synchronized(lock) {
        runCatching {
            pauseNative(handle = nativeHandle)
        }
    }

    fun stop() = synchronized(lock) {
        runCatching {
            stopNative(handle = nativeHandle)
        }
    }

    override fun close() = cleanable.clean()
}