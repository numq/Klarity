package com.github.numq.klarity.core.sampler

import com.github.numq.klarity.core.cleaner.NativeCleaner

internal class NativeSampler(sampleRate: Int, channels: Int) : AutoCloseable {
    private val nativeHandle by lazy {
        requireNotNull(createNative(
            sampleRate = sampleRate, channels = channels
        ).takeIf { it != -1L }) { "Could not instantiate native sampler" }
    }

    private val cleanable by lazy {
        NativeCleaner.cleaner.register(this) {
            deleteNative(handle = nativeHandle)
        }
    }

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

    fun setPlaybackSpeed(factor: Float) = setPlaybackSpeedNative(handle = nativeHandle, factor = factor)

    fun setVolume(value: Float) = setVolumeNative(handle = nativeHandle, value = value)

    fun start() = startNative(handle = nativeHandle)

    fun play(bytes: ByteArray, size: Int) = playNative(handle = nativeHandle, bytes = bytes, size = size)

    fun pause() = pauseNative(handle = nativeHandle)

    fun stop() = stopNative(handle = nativeHandle)

    override fun close() = cleanable.clean()
}
