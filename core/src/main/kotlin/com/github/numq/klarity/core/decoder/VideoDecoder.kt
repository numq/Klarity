package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.frame.NativeFrame
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal class VideoDecoder(
    private val nativeDecoder: NativeDecoder,
    override val media: Media.Video,
) : Decoder<Media.Video> {
    private val mutex = Mutex()

    override suspend fun decode() = mutex.withLock {
        nativeDecoder.format.mapCatching { format ->
            nativeDecoder.decodeVideo().mapCatching { nativeFrameInfo ->
                when (nativeFrameInfo) {
                    null -> Frame.EndOfStream

                    else -> with(nativeFrameInfo) {
                        when (getType()) {
                            null -> error("Unknown frame type")

                            NativeFrame.Type.AUDIO -> error("Audio frame is not supported by decoder")

                            NativeFrame.Type.VIDEO -> Frame.Content.Video(
                                buffer = buffer,
                                size = size,
                                timestamp = timestampMicros.microseconds,
                                isClosed = {
                                    nativeDecoder.isClosed() || buffer < 0L || size <= 0
                                },
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
        nativeDecoder.seekTo(timestamp.inWholeMicroseconds, keyframesOnly).map { timestampMicros ->
            timestampMicros.microseconds
        }
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