package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.format.NativeFormat
import com.github.numq.klarity.core.frame.NativeFrame
import java.lang.ref.Cleaner

internal class NativeDecoder(location: String, findAudioStream: Boolean, findVideoStream: Boolean) : AutoCloseable {
    private val nativeHandle: Long = createNative(location, findAudioStream, findVideoStream).also { handle ->
        require(handle != 0L) { "Unable to instantiate NativeDecoder" }
    }

    private val cleanable = cleaner.register(this) { deleteNative(nativeHandle) }

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

    val format: NativeFormat
        get() = getFormatNative(handle = nativeHandle)

    fun nextFrame(width: Int? = null, height: Int? = null) = nextFrameNative(
        handle = nativeHandle,
        width = width ?: format.width,
        height = height ?: format.height
    )

    fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) =
        seekToNative(handle = nativeHandle, timestampMicros = timestampMicros, keyframesOnly = keyframesOnly)

    fun reset() = resetNative(handle = nativeHandle)

    override fun close() = cleanable.clean()
}
