package com.github.numq.klarity.core.probe

import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media

object ProbeManager {
    fun probe(location: String) = Decoder.createProbeDecoder(
        location = location, findAudioStream = true, findVideoStream = true
    ).mapCatching { decoder ->
        decoder.use(Decoder<Media, Frame.Probe>::media)
    }
}