package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal class VideoDecoder(
    private val decoder: NativeDecoder,
    override val media: Media.Video,
) : Decoder<Media.Video, Frame.Video> {
    private val mutex = Mutex()

    override suspend fun decode() = mutex.withLock {
        decoder.format.mapCatching { format ->
            decoder.decodeVideo().map { nativeFrame ->
                when (nativeFrame) {
                    null -> Frame.Video.EndOfStream

                    else -> with(nativeFrame) {
                        Frame.Video.Content(
                            timestamp = timestampMicros.microseconds,
                            bufferHandle = bufferHandle,
                            bufferSize = bufferSize,
                            width = format.width,
                            height = format.height
                        )
                    }
                }
            }.getOrThrow()
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