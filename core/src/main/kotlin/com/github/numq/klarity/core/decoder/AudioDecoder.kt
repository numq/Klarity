package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.data.Data
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.frame.NativeFrame
import com.github.numq.klarity.core.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal class AudioDecoder(
    private val nativeDecoder: NativeDecoder,
    override val media: Media.Audio,
) : Decoder<Media.Audio> {
    private val mutex = Mutex()

    override suspend fun decode(data: Data) = mutex.withLock {
        nativeDecoder.decodeAudio(buffer = data.pointer, capacity = data.capacity).mapCatching { nativeFrame ->
            when (nativeFrame) {
                null -> Frame.EndOfStream

                else -> when (nativeFrame.getType()) {
                    null -> error("Unknown frame type")

                    NativeFrame.Type.AUDIO -> Frame.Content.Audio(
                        data = data,
                        remaining = nativeFrame.remaining,
                        timestamp = nativeFrame.timestampMicros.microseconds,
                        isClosed = data::isClosed
                    )

                    NativeFrame.Type.VIDEO -> error("Video frame is not supported by decoder")
                }
            }
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