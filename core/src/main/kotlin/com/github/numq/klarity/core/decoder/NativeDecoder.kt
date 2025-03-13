package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.exception.NativeException
import com.github.numq.klarity.core.format.NativeFormat
import com.github.numq.klarity.core.frame.NativeFrame
import java.lang.ref.Cleaner

internal class NativeDecoder(location: String, findAudioStream: Boolean, findVideoStream: Boolean) : AutoCloseable {
    private val nativeHandle = try {
        requireNotNull(
            createNative(
                location,
                findAudioStream,
                findVideoStream
            ).takeIf { it != -1L }
        ) { "Unable to instantiate native decoder" }
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
        private external fun createNative(location: String, findAudioStream: Boolean, findVideoStream: Boolean): Long

        @JvmStatic
        private external fun getFormatNative(handle: Long): NativeFormat

        @JvmStatic
        private external fun nextFrameNative(handle: Long, width: Int, height: Int): NativeFrame?

        @JvmStatic
        private external fun seekToNative(handle: Long, timestampMicros: Long, keyframesOnly: Boolean)

        @JvmStatic
        private external fun resetNative(handle: Long)

        @JvmStatic
        private external fun deleteNative(handle: Long)
    }

    val format: NativeFormat = try {
        getFormatNative(handle = nativeHandle)
    } catch (e: Exception) {
        throw NativeException(e)
    }

    fun nextFrame(width: Int? = null, height: Int? = null): NativeFrame? {
        try {
            return nextFrameNative(
                handle = nativeHandle,
                width = width ?: format.width,
                height = height ?: format.height
            )
        } catch (e: Exception) {
            throw NativeException(e)
        }
    }

    fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) {
        try {
            seekToNative(handle = nativeHandle, timestampMicros = timestampMicros, keyframesOnly = keyframesOnly)
        } catch (e: Exception) {
            throw NativeException(e)
        }
    }

    fun reset() {
        try {
            resetNative(handle = nativeHandle)
        } catch (e: Exception) {
            throw NativeException(e)
        }
    }

    override fun close() = cleanable.clean()
}
