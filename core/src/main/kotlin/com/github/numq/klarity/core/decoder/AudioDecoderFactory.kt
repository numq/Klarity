package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.factory.SuspendFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media

class AudioDecoderFactory : SuspendFactory<AudioDecoderFactory.Parameters, Decoder<Media.Audio, Frame.Audio>> {
    data class Parameters(val location: String, val sampleRate: Int?, val channels: Int?)

    override suspend fun create(parameters: Parameters) = with(parameters) {
        Decoder.createAudioDecoder(
            location = parameters.location,
            sampleRate = sampleRate,
            channels = channels
        )
    }
}