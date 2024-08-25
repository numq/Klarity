package decoder

import exception.JNIException
import frame.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import media.Media

internal class AudioDecoder(
    private val decoder: NativeDecoder,
    override val media: Media,
) : Decoder<Frame.Audio> {
    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    override suspend fun nextFrame() = withContext(coroutineContext) {
        runCatching {
            decoder.nextFrame(null, null)?.run {
                when (type) {
                    NativeFrame.Type.AUDIO.ordinal -> Frame.Audio.Content(
                        timestampMicros = timestampMicros,
                        bytes = bytes,
                        channels = decoder.format.channels,
                        sampleRate = decoder.format.sampleRate
                    )

                    else -> null
                }
            } ?: Frame.Audio.EndOfStream
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