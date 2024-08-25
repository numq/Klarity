package decoder

import media.Media

class ProbeDecoder(
    override val media: Media,
) : Decoder<Unit> {
    override suspend fun nextFrame() = Result.success(Unit)

    override suspend fun seekTo(micros: Long, keyframesOnly: Boolean) = Result.success(Unit)

    override suspend fun reset() = Result.success(Unit)

    override fun close() = Unit
}