package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.cleaner.NativeCleaner
import com.github.numq.klarity.core.format.NativeFormat
import com.github.numq.klarity.core.frame.NativeFrame
import java.io.Closeable

internal class NativeDecoder(
    location: String,
    findAudioStream: Boolean,
    prepareAudioStream: Boolean,
    findVideoStream: Boolean,
    prepareVideoStream: Boolean,
    hardwareAcceleration: Int,
    hardwareAccelerationFallbackCandidates: IntArray,
    useSoftwareAccelerationFallback: Boolean,
) : Closeable {
    private val nativeHandle by lazy {
        requireNotNull(createNative(
            location = location,
            findAudioStream = findAudioStream,
            prepareAudioStream = prepareAudioStream,
            findVideoStream = findVideoStream,
            prepareVideoStream = prepareVideoStream,
            hardwareAcceleration = hardwareAcceleration,
            hardwareAccelerationFallbackCandidates = hardwareAccelerationFallbackCandidates,
            useSoftwareAccelerationFallback = useSoftwareAccelerationFallback
        ).takeIf { it != -1L }) { "Could not instantiate native decoder" }
    }

    private val cleanable by lazy {
        NativeCleaner.cleaner.register(this) {
            deleteNative(handle = nativeHandle)
        }
    }

    companion object {
        fun getAvailableHardwareAcceleration() = getAvailableHardwareAccelerationNative() ?: intArrayOf()

        @JvmStatic
        private external fun getAvailableHardwareAccelerationNative(): IntArray?

        @JvmStatic
        private external fun createNative(
            location: String,
            findAudioStream: Boolean,
            prepareAudioStream: Boolean,
            findVideoStream: Boolean,
            prepareVideoStream: Boolean,
            hardwareAcceleration: Int,
            hardwareAccelerationFallbackCandidates: IntArray,
            useSoftwareAccelerationFallback: Boolean,
        ): Long

        @JvmStatic
        private external fun getFormatNative(handle: Long): NativeFormat

        @JvmStatic
        private external fun getHardwareAccelerationNative(handle: Long): Int

        @JvmStatic
        private external fun decodeNative(handle: Long, width: Int, height: Int): NativeFrame?

        @JvmStatic
        private external fun seekToNative(handle: Long, timestampMicros: Long, keyframesOnly: Boolean)

        @JvmStatic
        private external fun resetNative(handle: Long)

        @JvmStatic
        private external fun deleteNative(handle: Long)
    }

    val format by lazy { getFormatNative(handle = nativeHandle) }

    val hardwareAcceleration by lazy { getHardwareAccelerationNative(handle = nativeHandle) }

    fun decode(width: Int? = null, height: Int? = null) = decodeNative(
        handle = nativeHandle, width = width ?: format.width, height = height ?: format.height
    )

    fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) = seekToNative(
        handle = nativeHandle, timestampMicros = timestampMicros, keyframesOnly = keyframesOnly
    )

    fun reset() = resetNative(handle = nativeHandle)

    override fun close() = cleanable.clean()
}