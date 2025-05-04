package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.media.Media

internal class AudioDecoderFactory : Factory<AudioDecoderFactory.Parameters, Decoder<Media.Audio>> {
    data class Parameters(val location: String)

    override fun create(parameters: Parameters) = with(parameters) {
        Decoder.createAudioDecoder(location = location)
    }
}