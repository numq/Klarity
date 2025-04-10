package com.github.numq.klarity.core.probe

import com.github.numq.klarity.core.decoder.Decoder

object ProbeManager {
    suspend fun probe(location: String) =
        Decoder.createProbeDecoder(
            location = location,
            findAudioStream = true,
            findVideoStream = true,
        ).mapCatching { decoder ->
            try {
                decoder.media
            } finally {
                decoder.close().getOrThrow()
            }
        }
}