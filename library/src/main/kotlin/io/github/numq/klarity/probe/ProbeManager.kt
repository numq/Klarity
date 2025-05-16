package io.github.numq.klarity.probe

import io.github.numq.klarity.decoder.Decoder

object ProbeManager {
    fun probe(location: String) = Decoder.probe(
        location = location,
        findAudioStream = true,
        findVideoStream = true,
    )
}