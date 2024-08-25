package decoder

import exception.JNIException
import frame.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import media.Media

internal class VideoDecoder(
    private val decoder: NativeDecoder,
    override val media: Media,
) : Decoder<Frame.Video> {
    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    suspend fun nextFrame(width: Int?, height: Int?) = withContext(coroutineContext) {
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
        }.recoverCatching(JNIException::create)
    }

    override suspend fun nextFrame() = withContext(coroutineContext) {
        runCatching {
            decoder.nextFrame(null, null)?.run {
                when (type) {
                    NativeFrame.Type.VIDEO.ordinal -> Frame.Video.Content(
                        timestampMicros = timestampMicros,
                        bytes = bytes,
                        width = decoder.format.width,
                        height = decoder.format.height,
                        frameRate = decoder.format.frameRate
                    )

                    else -> null
                }
            } ?: Frame.Video.EndOfStream
        }.recoverCatching(JNIException::create)
    }

    override suspend fun seekTo(
        micros: Long,
        keyframesOnly: Boolean,
    ) = withContext(coroutineContext) {
        runCatching {
            decoder.seekTo(micros, keyframesOnly)
        }.recoverCatching(JNIException::create)
    }

    override suspend fun reset() = withContext(coroutineContext) {
        runCatching {
            decoder.reset()
        }.recoverCatching(JNIException::create)
    }

    override fun close() = runCatching {
        decoder.close()
    }.getOrDefault(Unit)
}