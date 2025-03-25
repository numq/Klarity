package com.github.numq.klarity.core.probe

import com.github.numq.klarity.core.closeable.use
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.decoder.HardwareAcceleration
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media

object ProbeManager {
    suspend fun probe(location: String, hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.NONE) =
        Decoder.createProbeDecoder(
            location = location,
            findAudioStream = true,
            findVideoStream = true,
            hardwareAcceleration = hardwareAcceleration
        ).mapCatching { decoder ->
            decoder.use(Decoder<Media, Frame.Probe>::media)
        }
}