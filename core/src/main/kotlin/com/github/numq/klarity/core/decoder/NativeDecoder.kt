package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.cleaner.NativeCleaner
import com.github.numq.klarity.core.format.NativeFormat
import com.github.numq.klarity.core.frame.NativeFrame
import java.io.Closeable

internal class NativeDecoder(
    location: String,
    audioFramePoolCapacity: Int,
    videoFramePoolCapacity: Int,
    sampleRate: Int? = null,
    channels: Int? = null,
    width: Int? = null,
    height: Int? = null,
    hardwareAccelerationCandidates: IntArray? = null,
) : Closeable {
    private object Native {
        @JvmStatic
        external fun getAvailableHardwareAcceleration(): IntArray?

        @JvmStatic
        external fun create(
            location: String,
            audioFramePoolCapacity: Int,
            videoFramePoolCapacity: Int,
            sampleRate: Int,
            channels: Int,
            width: Int,
            height: Int,
            hardwareAccelerationCandidates: IntArray,
        ): Long

        @JvmStatic
        external fun getFormat(handle: Long): NativeFormat

        @JvmStatic
        external fun decodeAudio(handle: Long): NativeFrame?

        @JvmStatic
        external fun decodeVideo(handle: Long): NativeFrame?

        @JvmStatic
        external fun seekTo(handle: Long, timestampMicros: Long, keyframesOnly: Boolean): Long

        @JvmStatic
        external fun reset(handle: Long)

        @JvmStatic
        external fun delete(handle: Long)
    }

    companion object {
        fun getAvailableHardwareAcceleration() = Native.getAvailableHardwareAcceleration() ?: intArrayOf()
    }

    private val lock = Any()

    internal var nativeHandle = -1L
        private set

    init {
        require(audioFramePoolCapacity >= 0 || videoFramePoolCapacity >= 0) { "Empty decoder is useless" }

        synchronized(lock) {
            nativeHandle = Native.create(
                location = location,
                audioFramePoolCapacity = audioFramePoolCapacity,
                videoFramePoolCapacity = videoFramePoolCapacity,
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
                    Native.delete(handle = nativeHandle)

                    nativeHandle = -1L
                }
            }.getOrDefault(Unit)
        }
    }

    val format = synchronized(lock) {
        runCatching {
            check(nativeHandle != -1L) { "Decoder has been closed" }

            Native.getFormat(handle = nativeHandle)
        }
    }

    fun decodeAudio() = synchronized(lock) {
        runCatching {
            Native.decodeAudio(handle = nativeHandle)
        }
    }

    fun decodeVideo() = synchronized(lock) {
        runCatching {
            Native.decodeVideo(handle = nativeHandle)
        }
    }

    fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) = synchronized(lock) {
        runCatching {
            Native.seekTo(
                handle = nativeHandle, timestampMicros = timestampMicros, keyframesOnly = keyframesOnly
            ).takeIf { it >= 0 } ?: timestampMicros
        }
    }

    fun reset() = synchronized(lock) {
        runCatching {
            Native.reset(handle = nativeHandle)
        }
    }

    override fun close() = cleanable.clean()
}