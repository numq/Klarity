package com.github.numq.klarity.core.sampler

import java.lang.ref.Cleaner

internal class NativeSampler(sampleRate: Int, channels: Int) : AutoCloseable {
    private val nativeHandle = createNative(sampleRate, channels).also { handle ->
        require(handle != 0L) { "Unable to instantiate NativeSampler" }
    }

    private val cleanable = cleaner.register(this) { deleteNative(nativeHandle) }

    companion object {
        private val cleaner = Cleaner.create()

        @JvmStatic
        private external fun createNative(sampleRate: Int, channels: Int): Long

        @JvmStatic
        private external fun setPlaybackSpeedNative(handle: Long, factor: Float)

        @JvmStatic
        private external fun setVolumeNative(handle: Long, value: Float)

        @JvmStatic
        private external fun startNative(handle: Long)

        @JvmStatic
        private external fun playNative(handle: Long, bytes: ByteArray, size: Int)

        @JvmStatic
        private external fun stopNative(handle: Long)

        @JvmStatic
        private external fun deleteNative(handle: Long)
    }

    fun setPlaybackSpeed(factor: Float) = setPlaybackSpeedNative(handle = nativeHandle, factor = factor)

    fun setVolume(value: Float) = setVolumeNative(handle = nativeHandle, value = value)

    fun start() = startNative(handle = nativeHandle)

    fun play(data: ByteArray, size: Int) = playNative(handle = nativeHandle, bytes = data, size = size)

    fun stop() = stopNative(handle = nativeHandle)

    override fun close() = cleanable.clean()
}