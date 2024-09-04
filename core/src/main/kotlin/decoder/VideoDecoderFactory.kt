package decoder

import factory.SuspendFactory
import frame.Frame
import media.Media

class VideoDecoderFactory : SuspendFactory<VideoDecoderFactory.Parameters, Decoder<Media.Video, Frame.Video>> {
    data class Parameters(val location: String)

    override suspend fun create(parameters: Parameters) = Decoder.createVideoDecoder(location = parameters.location)
}