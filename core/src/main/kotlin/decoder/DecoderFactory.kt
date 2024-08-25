package decoder

import factory.SuspendFactory

object DecoderFactory : SuspendFactory<DecoderFactory.Parameters, Decoder<*>> {
    sealed interface Parameters {
        val location: String

        data class Probe(
            override val location: String,
            val findAudioStream: Boolean,
            val findVideoStream: Boolean,
        ) : Parameters

        data class Audio(override val location: String) : Parameters

        data class Video(override val location: String) : Parameters
    }

    override suspend fun create(parameters: Parameters) = with(parameters) {
        when (this) {
            is Parameters.Probe -> Decoder.createProbeDecoder(
                location = location,
                findAudioStream = findAudioStream,
                findVideoStream = findVideoStream,
            )

            is Parameters.Audio -> Decoder.createAudioDecoder(location = location)

            is Parameters.Video -> Decoder.createVideoDecoder(location = location)
        }
    }
}