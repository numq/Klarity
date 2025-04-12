package com.github.numq.klarity.core.probe

import com.github.numq.klarity.core.decoder.Decoder

object ProbeManager {
    fun probe(location: String) = Decoder.probe(
        location = location,
        findAudioStream = true,
        findVideoStream = true,
    )
}