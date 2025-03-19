package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.format.NativeFormat
import com.github.numq.klarity.core.frame.NativeFrame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import java.lang.ref.Cleaner

internal class NativeDecoder(
    location: String,
    findAudioStream: Boolean,
    findVideoStream: Boolean,
    hardwareAcceleration: HardwareAcceleration,
) : AutoCloseable {
    private val nativeHandle = requireNotNull(
        createNative(
            location,
            findAudioStream,
            findVideoStream,
            hardwareAcceleration
        ).takeIf { it != -1L }
    ) { "Unable to instantiate native decoder" }

    private val cleanable = cleaner.register(this) {
        deleteNative(handle = nativeHandle)
    }

    companion object {
        private val cleaner = Cleaner.create()

        @JvmStatic
        private external fun createNative(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
            hardwareAcceleration: HardwareAcceleration,
        ): Long

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

    val format = getFormatNative(handle = nativeHandle)

    fun nextFrame(width: Int? = null, height: Int? = null) = nextFrameNative(
        handle = nativeHandle,
        width = width ?: format.width,
        height = height ?: format.height
    )

    fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) = seekToNative(
        handle = nativeHandle,
        timestampMicros = timestampMicros,
        keyframesOnly = keyframesOnly
    )

    fun reset() = resetNative(handle = nativeHandle)

    override fun close() = cleanable.clean()
}
