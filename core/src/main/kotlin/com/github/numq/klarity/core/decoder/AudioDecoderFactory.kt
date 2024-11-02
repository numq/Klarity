package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.factory.SuspendFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media

class AudioDecoderFactory : SuspendFactory<AudioDecoderFactory.Parameters, Decoder<Media.Audio, Frame.Audio>> {
    data class Parameters(val location: String)

    override suspend fun create(parameters: Parameters) = Decoder.createAudioDecoder(location = parameters.location)
}