package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.media.Media

internal class VideoDecoderFactory : Factory<VideoDecoderFactory.Parameters, Decoder<Media.Video>> {
    data class Parameters(
        val location: String,
        val hardwareAccelerationCandidates: List<HardwareAcceleration>?,
    )

    override fun create(parameters: Parameters) = with(parameters) {
        Decoder.createVideoDecoder(
            location = location,
            hardwareAccelerationCandidates = hardwareAccelerationCandidates
        )
    }
}