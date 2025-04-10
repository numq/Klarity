package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.frame.NativeFrame
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer

internal class VideoDecoder(
    private val decoder: NativeDecoder,
    override val media: Media.Video,
) : Decoder<Media.Video, Frame.Video> {
    private val mutex = Mutex()

    private var byteBuffer = ByteBuffer.allocateDirect(decoder.format.videoBufferSize)

    override suspend fun decode() = mutex.withLock {
        runCatching {
            byteBuffer.clear()

            decoder.decode(byteBuffer)?.let { nativeFrame ->
                when (nativeFrame.type) {
                    NativeFrame.Type.VIDEO.ordinal -> {
                        val bytes = ByteArray(byteBuffer.remaining())

                        byteBuffer.get(bytes)

                        Frame.Video.Content(
                            timestampMicros = nativeFrame.timestampMicros,
                            bytes = bytes,
                            width = decoder.format.width,
                            height = decoder.format.height
                        )
                    }

                    else -> null
                }
            } ?: Frame.Video.EndOfStream
        }
    }

    override suspend fun seekTo(micros: Long, keyframesOnly: Boolean) = mutex.withLock {
        runCatching {
            decoder.seekTo(micros, keyframesOnly).also {
                byteBuffer.clear()
            }
        }
    }

    override suspend fun reset() = mutex.withLock {
        runCatching {
            decoder.reset().also {
                byteBuffer.clear()
            }
        }
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            decoder.close()
        }
    }
}