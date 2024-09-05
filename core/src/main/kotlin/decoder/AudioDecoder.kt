package decoder

import exception.JNIException
import frame.Frame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import media.Media

internal class AudioDecoder(
    private val decoder: NativeDecoder,
    override val media: Media.Audio,
) : Decoder<Media.Audio, Frame.Audio> {
    private val mutex = Mutex()

    override suspend fun nextFrame(width: Int?, height: Int?) = mutex.withLock {
        runCatching {
            decoder.nextFrame(null, null)?.run {
                when (type) {
                    NativeFrame.Type.AUDIO.ordinal -> Frame.Audio.Content(
                        timestampMicros = timestampMicros,
                        bytes = bytes,
                        channels = decoder.format.channels(),
                        sampleRate = decoder.format.sampleRate()
                    )

                    else -> null
                }
            } ?: Frame.Audio.EndOfStream
        }.recoverCatching { t ->
            throw Exception("Audio decoder: ${t.message}", t)
        }.recoverCatching(JNIException::create)
    }

    override suspend fun seekTo(micros: Long, keyframesOnly: Boolean) = mutex.withLock {
        runCatching {
            decoder.seekTo(micros, keyframesOnly)
        }.recoverCatching { t ->
            throw Exception("Audio decoder: ${t.message}", t)
        }.recoverCatching(JNIException::create)
    }

    override suspend fun reset() = mutex.withLock {
        runCatching {
            decoder.reset()
        }.recoverCatching { t ->
            throw Exception("Audio decoder: ${t.message}", t)
        }.recoverCatching(JNIException::create)
    }

    override fun close() = runCatching { decoder.close() }.recoverCatching(JNIException::create).getOrThrow()
}