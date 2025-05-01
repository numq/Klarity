package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.cleaner.NativeCleaner
import com.github.numq.klarity.core.format.NativeFormat
import com.github.numq.klarity.core.frame.NativeFrame
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

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

        @JvmStatic
        external fun releaseAudioBuffer(handle: Long, buffer: Long)

        @JvmStatic
        external fun releaseVideoBuffer(handle: Long, buffer: Long)
    }

    companion object {
        fun getAvailableHardwareAcceleration() = Native.getAvailableHardwareAcceleration() ?: intArrayOf()
    }

    private val lock = Any()

    internal var nativeHandle = AtomicLong(-1L)
        private set

    private fun ensureOpen() {
        check(nativeHandle.get() != -1L) { "Native sampler is closed" }
    }

    internal fun isClosed() = nativeHandle.get() == -1L

    init {
        require(audioFramePoolCapacity >= 0 || videoFramePoolCapacity >= 0) { "Empty decoder is useless" }

        synchronized(lock) {
            nativeHandle.set(
                Native.create(
                    location = location,
                    audioFramePoolCapacity = audioFramePoolCapacity,
                    videoFramePoolCapacity = videoFramePoolCapacity,
                    sampleRate = sampleRate ?: 0,
                    channels = channels ?: 0,
                    width = width ?: 0,
                    height = height ?: 0,
                    hardwareAccelerationCandidates = hardwareAccelerationCandidates ?: intArrayOf()
                )
            )

            require(nativeHandle.get() != -1L) { "Could not instantiate native decoder" }
        }
    }

    private val cleanable = NativeCleaner.cleaner.register(this) {
        synchronized(lock) {
            ensureOpen()

            Native.delete(handle = nativeHandle.get())

            nativeHandle.set(-1L)
        }
    }

    val format = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.getFormat(handle = nativeHandle.get())
        }
    }

    fun decodeAudio() = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.decodeAudio(handle = nativeHandle.get())
        }
    }

    fun decodeVideo() = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.decodeVideo(handle = nativeHandle.get())
        }
    }

    fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.seekTo(
                handle = nativeHandle.get(), timestampMicros = timestampMicros, keyframesOnly = keyframesOnly
            ).takeIf { it >= 0 } ?: timestampMicros
        }
    }

    fun reset() = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.reset(handle = nativeHandle.get())
        }
    }

    fun releaseAudioBuffer(handle: Long) = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.releaseAudioBuffer(handle = nativeHandle.get(), buffer = handle)
        }
    }

    fun releaseVideoBuffer(handle: Long) = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.releaseVideoBuffer(handle = nativeHandle.get(), buffer = handle)
        }
    }

    override fun close() = cleanable.clean()
}