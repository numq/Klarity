package decoder

import exception.JNIException
import frame.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import media.Media

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
                        width = width ?: decoder.format.width(),
                        height = height ?: decoder.format.height(),
                        frameRate = decoder.format.frameRate()
                    )

                    else -> null
                }
            } ?: Frame.Video.EndOfStream
        }.recoverCatching { t ->
            throw Exception("Video decoder: ${t.message}", t)
        }.recoverCatching(JNIException::create)
    }

    override suspend fun seekTo(micros: Long, keyframesOnly: Boolean) = mutex.withLock {
        runCatching {
            decoder.seekTo(micros, keyframesOnly)
        }.recoverCatching { t ->
            throw Exception("Video decoder: ${t.message}", t)
        }.recoverCatching(JNIException::create)
    }

    override suspend fun reset() = mutex.withLock {
        runCatching {
            decoder.reset()
        }.recoverCatching { t ->
            throw Exception("Video decoder: ${t.message}", t)
        }.recoverCatching(JNIException::create)
    }

    override fun close() = runCatching { decoder.close() }.recoverCatching(JNIException::create).getOrThrow()
}