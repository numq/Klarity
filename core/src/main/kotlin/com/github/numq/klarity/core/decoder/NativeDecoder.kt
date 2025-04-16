package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.cleaner.NativeCleaner
import com.github.numq.klarity.core.format.NativeFormat
import com.github.numq.klarity.core.frame.NativeAudioFrame
import com.github.numq.klarity.core.frame.NativeVideoFrame
import java.io.Closeable
import java.nio.ByteBuffer

internal class NativeDecoder(
    location: String,
    findAudioStream: Boolean,
    findVideoStream: Boolean,
    decodeAudioStream: Boolean,
    decodeVideoStream: Boolean,
    sampleRate: Int? = null,
    channels: Int? = null,
    width: Int? = null,
    height: Int? = null,
    frameRate: Double? = null,
    hardwareAccelerationCandidates: IntArray? = null,
) : Closeable {
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
        private external fun decodeAudioNative(handle: Long): NativeAudioFrame?

        @JvmStatic
        private external fun decodeVideoNative(handle: Long, byteBuffer: ByteBuffer): NativeVideoFrame?

        @JvmStatic
        private external fun seekToNative(handle: Long, timestampMicros: Long, keyframesOnly: Boolean)

        @JvmStatic
        private external fun resetNative(handle: Long)

        @JvmStatic
        private external fun deleteNative(handle: Long)
    }

    private val lock = Any()

    internal var nativeHandle = -1L

    init {
        synchronized(lock) {
            nativeHandle = createNative(
                location = location,
                findAudioStream = findAudioStream,
                findVideoStream = findVideoStream,
                decodeAudioStream = decodeAudioStream,
                decodeVideoStream = decodeVideoStream,
                sampleRate = sampleRate ?: 0,
                channels = channels ?: 0,
                width = width ?: 0,
                height = height ?: 0,
                frameRate = frameRate ?: .0,
                hardwareAccelerationCandidates = hardwareAccelerationCandidates ?: intArrayOf()
            )

            require(nativeHandle != -1L) { "Could not instantiate native decoder" }
        }
    }

    private val cleanable = NativeCleaner.cleaner.register(this) {
        synchronized(lock) {
            deleteNative(handle = nativeHandle)

            nativeHandle = -1L
        }
    }

    val format = synchronized(lock) {
        check(nativeHandle != -1L) { "Decoder has been closed" }

        getFormatNative(handle = nativeHandle)
    }


    fun decodeAudio() = synchronized(lock) {
        decodeAudioNative(handle = nativeHandle)
    }

    fun decodeVideo(byteBuffer: ByteBuffer) = synchronized(lock) {
        decodeVideoNative(handle = nativeHandle, byteBuffer = byteBuffer)
    }

    fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) = synchronized(lock) {
        seekToNative(
            handle = nativeHandle, timestampMicros = timestampMicros, keyframesOnly = keyframesOnly
        )
    }

    fun reset() = synchronized(lock) {
        resetNative(handle = nativeHandle)
    }

    override fun close() = cleanable.clean()
}