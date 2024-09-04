package decoder

import factory.SuspendFactory
import frame.Frame
import media.Media

class ProbeDecoderFactory : SuspendFactory<ProbeDecoderFactory.Parameters, Decoder<Media, Frame.Probe>> {
    data class Parameters(
        val location: String,
        val findAudioStream: Boolean,
        val findVideoStream: Boolean,
    )

    override suspend fun create(parameters: Parameters) = with(parameters) {
        Decoder.createProbeDecoder(
            location = location,
            findAudioStream = findAudioStream,
            findVideoStream = findVideoStream
        )
    }
}