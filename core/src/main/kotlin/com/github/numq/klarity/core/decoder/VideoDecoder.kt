package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.frame.NativeFrame
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class VideoDecoder(
    private val decoder: NativeDecoder,
    override val media: Media.Video,
) : Decoder<Media.Video, Frame.Video> {
    private val mutex = Mutex()

    override suspend fun nextFrame(width: Int?, height: Int?) = mutex.withLock {
        runCatching {
            decoder.nextFrame(width, height)?.run {
                when (type) {
                    NativeFrame.Type.VIDEO.ordinal -> Frame.Video.Content(
                        timestampMicros = timestampMicros,
                        bytes = bytes,
                        width = width ?: decoder.format.width,
                        height = height ?: decoder.format.height,
                        frameRate = decoder.format.frameRate
                    )

                    else -> null
                }
            } ?: Frame.Video.EndOfStream
        }
    }

    override suspend fun seekTo(micros: Long, keyframesOnly: Boolean) = mutex.withLock {
        runCatching {
            decoder.seekTo(micros, keyframesOnly)
        }
    }

    override suspend fun reset() = mutex.withLock {
        runCatching {
            decoder.reset()
        }
    }

    override suspend fun close() = mutex.withLock {
        decoder.close()
    }
}