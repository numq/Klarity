package io.github.numq.klarity.decoder

import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.media.Media
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.Data
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal class VideoDecoder(
    private val nativeDecoder: NativeDecoder,
    override val media: Media.Video,
) : Decoder<Media.Video> {
    private val mutex = Mutex()

    override suspend fun decodeAudio() = error("Decoder does not support audio")

    override suspend fun decodeVideo(data: Data) = mutex.withLock {
        nativeDecoder.format.mapCatching { format ->
            nativeDecoder.decodeVideo(buffer = data.writableData(), capacity = data.size).mapCatching { nativeFrame ->
                when (nativeFrame) {
                    null -> Frame.EndOfStream

                    else -> with(nativeFrame) {
                        Frame.Content.Video(
                            data = data.makeSubset(0, remaining),
                            timestamp = timestampMicros.microseconds,
                            width = format.width,
                            height = format.height
                        )
                    }
                }
            }.getOrThrow()
        }
    }

    override suspend fun seekTo(timestamp: Duration, keyFramesOnly: Boolean) = mutex.withLock {
        nativeDecoder.seekTo(timestamp.inWholeMicroseconds, keyFramesOnly)
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