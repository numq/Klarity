package io.github.numq.klarity.sampler

import io.github.numq.klarity.cleaner.NativeCleaner
import java.io.Closeable
import java.util.concurrent.atomic.AtomicLong

internal class NativeSampler(sampleRate: Int, channels: Int) : Closeable {
    private object Native {
        @JvmStatic
        external fun create(sampleRate: Int, channels: Int): Long

        @JvmStatic
        external fun start(handle: Long): Long

        @JvmStatic
        external fun write(handle: Long, bytes: ByteArray, volume: Float, playbackSpeedFactor: Float)

        @JvmStatic
        external fun stop(handle: Long)

        @JvmStatic
        external fun flush(handle: Long)

        @JvmStatic
        external fun drain(handle: Long, volume: Float, playbackSpeedFactor: Float)

        @JvmStatic
        external fun delete(handle: Long)
    }

    private val nativeHandle = AtomicLong(-1L)

    private val cleanable = NativeCleaner.cleaner.register(this) {
        val handle = nativeHandle.get()

        if (handle != -1L && nativeHandle.compareAndSet(handle, -1L)) {
            Native.delete(handle = handle)
        }
    }

    private fun ensureOpen() {
        check(nativeHandle.get() != -1L) { "Native sampler is closed" }
    }

    init {
        require(sampleRate > 0) { "Invalid sample rate" }

        require(channels > 0) { "Invalid channels" }

        nativeHandle.set(Native.create(sampleRate = sampleRate, channels = channels))

        require(nativeHandle.get() != -1L) { "Could not instantiate native sampler" }
    }

    fun start() = runCatching {
        ensureOpen()

        Native.start(handle = nativeHandle.get())
    }

    fun write(bytes: ByteArray, volume: Float, playbackSpeedFactor: Float) = runCatching {
        ensureOpen()

        require(volume in 0.0..1.0) { "Volume must be between 0.0 and 1.0" }

        require(playbackSpeedFactor in 0.5..2.0) { "Playback speed factor must be between 0.5 and 2.0" }

        Native.write(
            handle = nativeHandle.get(),
            bytes = bytes,
            volume = volume,
            playbackSpeedFactor = playbackSpeedFactor
        )
    }

    fun stop() = runCatching {
        ensureOpen()

        Native.stop(handle = nativeHandle.get())
    }

    fun flush() = runCatching {
        ensureOpen()

        Native.flush(handle = nativeHandle.get())
    }

    fun drain(volume: Float, playbackSpeedFactor: Float) = runCatching {
        ensureOpen()

        require(volume in 0.0..1.0) { "Volume must be between 0.0 and 1.0" }

        require(playbackSpeedFactor in 0.5..2.0) { "Playback speed factor must be between 0.5 and 2.0" }

        Native.drain(handle = nativeHandle.get(), volume = volume, playbackSpeedFactor = playbackSpeedFactor)
    }

    override fun close() = cleanable.clean()
}