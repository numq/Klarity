package decoder

import frame.Frame
import media.Media

internal class VideoDecoder(
    private val decoder: NativeDecoder,
    override val media: Media,
) : Decoder<Frame.Video> {
    override suspend fun nextFrame() = runCatching {
        decoder.nextFrame()?.run {
            when (type) {
                NativeFrame.Type.VIDEO.ordinal -> Frame.Video.Content(
                    timestampMicros = timestampMicros,
                    bytes = bytes,
                    width = decoder.format.width,
                    height = decoder.format.height,
                    frameRate = decoder.format.frameRate.toInt()
                )

                else -> null
            }
        } ?: Frame.Video.EndOfMedia(
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