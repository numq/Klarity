package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.frame.NativeFrame
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.nio.ByteBuffer

internal class AudioDecoder(
    private val decoder: NativeDecoder,
    override val media: Media.Audio,
) : Decoder<Media.Audio, Frame.Audio> {
    private val mutex = Mutex()

    private var byteBuffer = ByteBuffer.allocateDirect(decoder.format.audioBufferSize)

    override suspend fun decode() = mutex.withLock {
        runCatching {
            byteBuffer.clear()

            decoder.decode(byteBuffer)?.let { nativeFrame ->
                when (nativeFrame.type) {
                    NativeFrame.Type.AUDIO.ordinal -> {
                        val bytes = ByteArray(byteBuffer.remaining())

                        byteBuffer.get(bytes)

                        Frame.Audio.Content(
                            timestampMicros = nativeFrame.timestampMicros,
                            bytes = bytes
                        )
                    }

                    else -> null
                }
            } ?: Frame.Audio.EndOfStream
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