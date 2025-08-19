package io.github.numq.klarity.decoder

import io.github.numq.klarity.factory.Factory
import io.github.numq.klarity.format.Format

internal class AudioDecoderFactory : Factory<AudioDecoderFactory.Parameters, Decoder<Format.Audio>> {
    data class Parameters(val location: String)

    override fun create(parameters: Parameters) = with(parameters) {
        Decoder.createAudioDecoder(location = location)
    }
}