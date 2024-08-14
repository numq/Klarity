package decoder

import media.Media

class ProbeDecoder(
    override val media: Media,
) : Decoder<Unit> {
    override suspend fun nextFrame() = Result.success(Unit)

    override fun seekTo(micros: Long) = Result.success(Unit)

    override fun reset() = Result.success(Unit)

    override fun close() = Unit
}