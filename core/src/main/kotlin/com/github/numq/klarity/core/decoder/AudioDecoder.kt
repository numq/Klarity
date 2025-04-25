package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal class AudioDecoder(
    private val decoder: NativeDecoder,
    override val media: Media.Audio,
) : Decoder<Media.Audio, Frame.Audio> {
    private val mutex = Mutex()

    override suspend fun decode() = mutex.withLock {
        decoder.decodeAudio().map { nativeFrame ->
            when (nativeFrame) {
                null -> Frame.Audio.EndOfStream

                else -> with(nativeFrame) {
                    Frame.Audio.Content(
                        timestamp = timestampMicros.microseconds,
                        bufferHandle = bufferHandle,
                        bufferSize = bufferSize
                    )
                }
            }
        }
    }

    override suspend fun seekTo(timestamp: Duration, keyframesOnly: Boolean) = mutex.withLock {
        decoder.seekTo(timestamp.inWholeMicroseconds, keyframesOnly).map { timestampMicros ->
            timestampMicros.microseconds
        }
    }

    override suspend fun reset() = mutex.withLock {
        decoder.reset()
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            decoder.close()
        }
    }
}