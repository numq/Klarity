package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.media.Media

class MediaDecoderFactory : Factory<MediaDecoderFactory.Parameters, Decoder<Media.AudioVideo>> {
    data class Parameters(
        val location: String,
        val sampleRate: Int?,
        val channels: Int?,
        val width: Int?,
        val height: Int?,
        val hardwareAccelerationCandidates: List<HardwareAcceleration>?,
    )

    override fun create(parameters: Parameters) = with(parameters) {
        Decoder.createMediaDecoder(
            location = location,
            sampleRate = sampleRate,
            channels = channels,
            width = width,
            height = height,
            hardwareAccelerationCandidates = hardwareAccelerationCandidates
        )
    }
}