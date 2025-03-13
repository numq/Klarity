package com.github.numq.klarity.core.sampler

import com.github.numq.klarity.core.exception.NativeException
import java.lang.ref.Cleaner

internal class NativeSampler(sampleRate: Int, channels: Int) : AutoCloseable {
    private val nativeHandle = try {
        requireNotNull(
            createNative(
                sampleRate,
                channels
            ).takeIf { it != -1L }
        ) { "Unable to instantiate native sampler" }
    } catch (e: Exception) {
        throw NativeException(e)
    }

    private val cleanable = cleaner.register(this) {
        try {
            deleteNative(handle = nativeHandle)
        } catch (e: Exception) {
            throw NativeException(e)
        }
    }

    companion object {
        private val cleaner = Cleaner.create()

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
        private external fun stopNative(handle: Long)

        @JvmStatic
        private external fun deleteNative(handle: Long)
    }

    fun setPlaybackSpeed(factor: Float) {
        try {
            setPlaybackSpeedNative(handle = nativeHandle, factor = factor)
        } catch (e: Exception) {
            throw NativeException(e)
        }
    }

    fun setVolume(value: Float) {
        try {
            setVolumeNative(handle = nativeHandle, value = value)
        } catch (e: Exception) {
            throw NativeException(e)
        }
    }

    fun start(): Long {
        try {
            return startNative(handle = nativeHandle)
        } catch (e: Exception) {
            throw NativeException(e)
        }
    }

    fun play(data: ByteArray, size: Int) {
        try {
            playNative(handle = nativeHandle, bytes = data, size = size)
        } catch (e: Exception) {
            throw NativeException(e)
        }
    }

    fun stop() {
        try {
            stopNative(handle = nativeHandle)
        } catch (e: Exception) {
            throw NativeException(e)
        }
    }

    override fun close() = cleanable.clean()
}
