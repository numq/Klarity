package com.github.numq.klarity.core.probe

import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.hwaccel.HardwareAccelerationFallback

object ProbeManager {
    suspend fun probe(
        location: String,
        hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.None,
        hardwareAccelerationFallback: HardwareAccelerationFallback = HardwareAccelerationFallback(),
    ) =
        Decoder.createProbeDecoder(
            location = location,
            findAudioStream = true,
            findVideoStream = true,
            hardwareAcceleration = hardwareAcceleration,
            hardwareAccelerationFallback = hardwareAccelerationFallback,
        ).mapCatching { decoder ->
            try {
                decoder.media
            } finally {
                decoder.close().getOrThrow()
            }
        }
}