package decoder

import frame.Frame
import media.Media

internal class AudioDecoder(
    private val decoder: NativeDecoder,
    override val media: Media,
) : Decoder<Frame.Audio> {
    override suspend fun nextFrame() = runCatching {
        decoder.nextFrame()?.run {
            when (type) {
                NativeFrame.Type.AUDIO.ordinal -> Frame.Audio.Content(
                    timestampMicros = timestampMicros,
                    bytes = bytes,
                    channels = decoder.format.channels,
                    sampleRate = decoder.format.sampleRate
                )

                else -> null
            }
        } ?: Frame.Audio.EndOfMedia(
            timestampMicros = decoder.format.durationMicros
        )
    }

    override fun seekTo(micros: Long) = runCatching {
        decoder.seekTo(micros)
    }

    override fun reset() = runCatching {
        decoder.reset()
    }

    override fun close() = runCatching {
        decoder.close()
    }.getOrDefault(Unit)
}