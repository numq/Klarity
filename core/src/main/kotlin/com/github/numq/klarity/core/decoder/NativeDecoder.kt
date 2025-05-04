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

    private val nativeHandle = AtomicLong(-1L)

    private val cleanable = NativeCleaner.cleaner.register(this) {
        val handle = nativeHandle.get()

        if (handle != -1L && nativeHandle.compareAndSet(handle, -1L)) {
            Native.delete(handle = handle)
        }
    }

    private fun ensureOpen() {
        check(nativeHandle.get() != -1L) { "Native decoder is closed" }
    }

    init {
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

    val format = runCatching {
        ensureOpen()

        Native.getFormat(handle = nativeHandle.get())
    }

    fun getNativeHandle(): Long {
        ensureOpen()

        return nativeHandle.get()
    }

    fun decodeAudio(buffer: Long, capacity: Int) = runCatching {
        ensureOpen()

        Native.decodeAudio(handle = nativeHandle.get(), buffer = buffer, capacity = capacity)
    }

    fun decodeVideo(buffer: Long, capacity: Int) = runCatching {
        ensureOpen()

        Native.decodeVideo(handle = nativeHandle.get(), buffer = buffer, capacity = capacity)
    }

    fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) = runCatching {
        ensureOpen()

        Native.seekTo(
            handle = nativeHandle.get(), timestampMicros = timestampMicros, keyframesOnly = keyframesOnly
        ).takeIf { it >= 0 } ?: timestampMicros
    }

    fun reset() = runCatching {
        ensureOpen()

        Native.reset(handle = nativeHandle.get())
    }

    override fun close() = cleanable.clean()
}