package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.cleaner.NativeCleaner
import com.github.numq.klarity.core.format.NativeFormat
import com.github.numq.klarity.core.frame.NativeFrame
import java.io.Closeable

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
            hardwareAccelerationCandidates: IntArray,
        ): Long

        @JvmStatic
        private external fun getFormatNative(handle: Long): NativeFormat

        @JvmStatic
        private external fun decodeAudioNative(handle: Long): NativeFrame?

        @JvmStatic
        private external fun decodeVideoNative(handle: Long): NativeFrame?

        @JvmStatic
        private external fun decodeMediaNative(handle: Long): NativeFrame?

        @JvmStatic
        private external fun seekToNative(handle: Long, timestampMicros: Long, keyframesOnly: Boolean): Long

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
                hardwareAccelerationCandidates = hardwareAccelerationCandidates ?: intArrayOf()
            )

            require(nativeHandle != -1L) { "Could not instantiate native decoder" }
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

    val format = synchronized(lock) {
        runCatching {
            check(nativeHandle != -1L) { "Decoder has been closed" }

            getFormatNative(handle = nativeHandle)
        }
    }

    fun decodeAudio() = synchronized(lock) {
        runCatching {
            decodeAudioNative(handle = nativeHandle)
        }
    }

    fun decodeVideo() = synchronized(lock) {
        runCatching {
            decodeVideoNative(handle = nativeHandle)
        }
    }

    fun decodeMedia() = synchronized(lock) {
        runCatching {
            decodeMediaNative(handle = nativeHandle)
        }
    }

    fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) = synchronized(lock) {
        runCatching {
            seekToNative(
                handle = nativeHandle, timestampMicros = timestampMicros, keyframesOnly = keyframesOnly
            ).takeIf { it >= 0 } ?: timestampMicros
        }
    }

    fun reset() = synchronized(lock) {
        runCatching {
            resetNative(handle = nativeHandle)
        }
    }

    override fun close() = cleanable.clean()
}