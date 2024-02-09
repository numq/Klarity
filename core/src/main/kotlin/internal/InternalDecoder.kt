package internal

import decoder.NativeDecoder
import frame.DecodedFrame
import media.MediaInfo

interface InternalDecoder : AutoCloseable {
    companion object {
        fun create(location: String): InternalDecoder = DefaultInternalDecoder(NativeDecoder(location))
    }

    val id: Long
    val info: MediaInfo
    fun readFrame(doVideo: Boolean, doAudio: Boolean): DecodedFrame?
    fun seekTo(timestampMicros: Long)
}