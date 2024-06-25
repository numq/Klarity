package decoder

import media.Media

class ProbeDecoder(
    private val decoder: NativeDecoder,
    override val media: Media,
) : Decoder<Nothing> {
    override suspend fun nextFrame() = Result.success(null)

    override fun seekTo(micros: Long) = Result.success(Unit)

    override fun reset() = Result.success(Unit)

    override fun close() = runCatching { decoder.close() }.getOrDefault(Unit)
}