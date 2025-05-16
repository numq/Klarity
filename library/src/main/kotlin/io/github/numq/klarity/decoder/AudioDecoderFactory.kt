package io.github.numq.klarity.decoder

import io.github.numq.klarity.factory.Factory
import io.github.numq.klarity.media.Media

internal class AudioDecoderFactory : Factory<AudioDecoderFactory.Parameters, Decoder<Media.Audio>> {
    data class Parameters(val location: String)

    override fun create(parameters: Parameters) = with(parameters) {
        Decoder.createAudioDecoder(location = location)
    }
}