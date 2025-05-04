package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.cleaner.NativeCleaner
import com.github.numq.klarity.core.format.NativeFormat
import com.github.numq.klarity.core.frame.NativeFrame
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

internal class NativeDecoder(
    location: String,
    findAudioStream: Boolean,
    findVideoStream: Boolean,
    decodeAudioStream: Boolean,
    decodeVideoStream: Boolean,
    hardwareAccelerationCandidates: IntArray? = null,
) : Closeable {
    private object Native {
        @JvmStatic
        external fun getAvailableHardwareAcceleration(): IntArray?

        @JvmStatic
        external fun create(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
            decodeAudioStream: Boolean,
            decodeVideoStream: Boolean,
            hardwareAccelerationCandidates: IntArray
        ): Long

        @JvmStatic
        external fun getFormat(handle: Long): NativeFormat

        @JvmStatic
        external fun decodeAudio(handle: Long, buffer: Long, capacity: Int): NativeFrame?

        @JvmStatic
        external fun decodeVideo(handle: Long, buffer: Long, capacity: Int): NativeFrame?

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

    private val nativeHandle = AtomicLong(-1L)

    private fun ensureOpen() {
        check(nativeHandle.get() != -1L) { "Native sampler is closed" }
    }

    init {
        synchronized(lock) {
            nativeHandle.set(
                Native.create(
                    location = location,
                    findAudioStream = findAudioStream,
                    findVideoStream = findVideoStream,
                    decodeAudioStream = decodeAudioStream,
                    decodeVideoStream = decodeVideoStream,
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

    fun getNativeHandle() = nativeHandle.get()

    fun decodeAudio(buffer: Long, capacity: Int) = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.decodeAudio(handle = nativeHandle.get(), buffer = buffer, capacity = capacity)
        }
    }

    fun decodeVideo(buffer: Long, capacity: Int) = synchronized(lock) {
        runCatching {
            ensureOpen()

            Native.decodeVideo(handle = nativeHandle.get(), buffer = buffer, capacity = capacity)
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

    override fun close() = cleanable.clean()
}