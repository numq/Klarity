package probe

import decoder.Decoder

object ProbeManager {
    fun probe(location: String) = Decoder.createProbeDecoder(
        location = location,
        findAudioStream = true,
        findVideoStream = true
    ).mapCatching { decoder ->
        decoder.use(Decoder<*>::media)
    }
}