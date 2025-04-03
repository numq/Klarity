package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.factory.SuspendFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.hwaccel.HardwareAccelerationFallback
import com.github.numq.klarity.core.media.Media

class ProbeDecoderFactory : SuspendFactory<ProbeDecoderFactory.Parameters, Decoder<Media, Frame.Probe>> {
    data class Parameters(
        val location: String,
        val findAudioStream: Boolean,
        val findVideoStream: Boolean,
        val hardwareAcceleration: HardwareAcceleration,
        val hardwareAccelerationFallback: HardwareAccelerationFallback,
    )

    override suspend fun create(parameters: Parameters) = with(parameters) {
        Decoder.createProbeDecoder(
            location = location,
            findAudioStream = findAudioStream,
            findVideoStream = findVideoStream,
            hardwareAcceleration = hardwareAcceleration,
            hardwareAccelerationFallback = hardwareAccelerationFallback
        )
    }
}