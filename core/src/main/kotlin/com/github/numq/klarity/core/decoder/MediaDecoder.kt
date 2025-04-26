package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.frame.NativeFrame
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal class MediaDecoder(
    private val decoder: NativeDecoder,
    override val media: Media.AudioVideo,
) : Decoder<Media.AudioVideo> {
    private val mutex = Mutex()

    override suspend fun decode() = mutex.withLock {
        decoder.format.mapCatching { format ->
            decoder.decodeMedia().map { nativeFrame ->
                when (nativeFrame) {
                    null -> Frame.EndOfStream

                    else -> with(nativeFrame) {
                        when (getType()) {
                            null -> error("Unknown frame type")

                            NativeFrame.Type.AUDIO -> Frame.Content.Audio(
                                timestamp = timestampMicros.microseconds,
                                bufferHandle = bufferHandle,
                                bufferSize = bufferSize
                            )

                            NativeFrame.Type.VIDEO -> Frame.Content.Video(
                                timestamp = timestampMicros.microseconds,
                                bufferHandle = bufferHandle,
                                bufferSize = bufferSize,
                                width = format.width,
                                height = format.height
                            )
                        }
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