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
        private external fun playNative(handle: Long, bytes: ByteArray, size: Int)

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
            deleteNative(handle = nativeHandle)

            nativeHandle = -1L
        }
    }

    fun setPlaybackSpeed(factor: Float) = synchronized(lock) {
        setPlaybackSpeedNative(handle = nativeHandle, factor = factor)
    }

    fun setVolume(value: Float) = synchronized(lock) {
        setVolumeNative(handle = nativeHandle, value = value)
    }

    fun start() = synchronized(lock) {
        startNative(handle = nativeHandle)
    }

    fun play(bytes: ByteArray, size: Int) = synchronized(lock) {
        playNative(handle = nativeHandle, bytes = bytes, size = size)
    }

    fun pause() = synchronized(lock) {
        pauseNative(handle = nativeHandle)
    }

    fun stop() = synchronized(lock) {
        stopNative(handle = nativeHandle)
    }

    override fun close() = cleanable.clean()
}