package io.github.numq.klarity.decoder

import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.Data
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal class AudioDecoder(
    private val nativeDecoder: NativeDecoder,
    override val media: Media.Audio,
) : Decoder<Media.Audio> {
    private val mutex = Mutex()

    override suspend fun decodeAudio() = mutex.withLock {
        nativeDecoder.decodeAudio().mapCatching { nativeFrame ->
            when (nativeFrame) {
                null -> Frame.EndOfStream

                else -> with(nativeFrame) {
                    Frame.Content.Audio(
                        bytes = bytes,
                        timestamp = timestampMicros.microseconds
                    )
                }
            }
        }
    }

    override suspend fun decodeVideo(data: Data) = error("Decoder does not support video")

    override suspend fun seekTo(timestamp: Duration, keyframesOnly: Boolean) = mutex.withLock {
        nativeDecoder.seekTo(timestamp.inWholeMicroseconds, keyframesOnly)
    }

    override suspend fun reset() = mutex.withLock {
        nativeDecoder.reset()
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            nativeDecoder.close()
        }
    }
}