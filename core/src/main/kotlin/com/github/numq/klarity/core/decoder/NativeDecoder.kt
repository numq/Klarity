package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.cleaner.NativeCleaner
import com.github.numq.klarity.core.format.NativeFormat
import com.github.numq.klarity.core.frame.NativeFrame
import java.io.Closeable
import java.nio.ByteBuffer

internal class NativeDecoder(
    location: String,
    findAudioStream: Boolean,
    findVideoStream: Boolean,
    decodeAudioStream: Boolean,
    decodeVideoStream: Boolean,
    sampleRate: Int,
    channels: Int,
    width: Int,
    height: Int,
    frameRate: Double,
    hardwareAccelerationCandidates: IntArray,
) : Closeable {
    private val nativeHandle by lazy {
        requireNotNull(createNative(
            location = location,
            findAudioStream = findAudioStream,
            findVideoStream = findVideoStream,
            decodeAudioStream = decodeAudioStream,
            decodeVideoStream = decodeVideoStream,
            sampleRate = sampleRate,
            channels = channels,
            width = width,
            height = height,
            frameRate = frameRate,
            hardwareAccelerationCandidates = hardwareAccelerationCandidates
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
            findVideoStream: Boolean,
            decodeAudioStream: Boolean,
            decodeVideoStream: Boolean,
            sampleRate: Int,
            channels: Int,
            width: Int,
            height: Int,
            frameRate: Double,
            hardwareAccelerationCandidates: IntArray,
        ): Long

        @JvmStatic
        private external fun getFormatNative(handle: Long): NativeFormat

        @JvmStatic
        private external fun decodeNative(handle: Long, byteBuffer: ByteBuffer): NativeFrame?

        @JvmStatic
        private external fun seekToNative(handle: Long, timestampMicros: Long, keyframesOnly: Boolean)

        @JvmStatic
        private external fun resetNative(handle: Long)

        @JvmStatic
        private external fun deleteNative(handle: Long)
    }

    val format by lazy { getFormatNative(handle = nativeHandle) }

    fun decode(byteBuffer: ByteBuffer) = decodeNative(handle = nativeHandle, byteBuffer = byteBuffer)

    fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) = seekToNative(
        handle = nativeHandle, timestampMicros = timestampMicros, keyframesOnly = keyframesOnly
    )

    fun reset() = resetNative(handle = nativeHandle)

    override fun close() = cleanable.clean()
}