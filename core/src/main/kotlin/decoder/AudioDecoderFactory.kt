package decoder

import factory.SuspendFactory
import frame.Frame
import media.Media

class AudioDecoderFactory : SuspendFactory<AudioDecoderFactory.Parameters, Decoder<Media.Audio, Frame.Audio>> {
    data class Parameters(val location: String)

    override suspend fun create(parameters: Parameters) = Decoder.createAudioDecoder(location = parameters.location)
}