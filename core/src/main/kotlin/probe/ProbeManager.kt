package probe

import decoder.Decoder
import frame.Frame
import media.Media

object ProbeManager {
    fun probe(location: String) = Decoder.createProbeDecoder(
        location = location, findAudioStream = true, findVideoStream = true
    ).mapCatching { decoder ->
        decoder.use(Decoder<Media, Frame.Probe>::media)
    }
}