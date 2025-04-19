package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class AudioDecoder(
    private val decoder: NativeDecoder,
    override val media: Media.Audio,
) : Decoder<Media.Audio, Frame.Audio> {
    private val mutex = Mutex()

    override suspend fun decode() = mutex.withLock {
        runCatching {
            decoder.decodeAudio()?.run {
                Frame.Audio.Content(
                    timestampMicros = timestampMicros,
                    bytes = audioBytes
                )
            } ?: Frame.Audio.EndOfStream
        }
    }

    override suspend fun seekTo(timestampMicros: Long, keyframesOnly: Boolean) = mutex.withLock {
        runCatching {
            decoder.seekTo(timestampMicros, keyframesOnly)
        }
    }

    override suspend fun reset() = mutex.withLock {
        runCatching {
            decoder.reset()
        }
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            decoder.close()
        }
    }
}