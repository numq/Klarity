package io.github.numq.klarity.decoder

import io.github.numq.klarity.factory.Factory
import io.github.numq.klarity.format.Format
import io.github.numq.klarity.hwaccel.HardwareAcceleration

internal class VideoDecoderFactory : Factory<VideoDecoderFactory.Parameters, Decoder<Format.Video>> {
    data class Parameters(
        val location: String,
        val hardwareAccelerationCandidates: List<HardwareAcceleration>?,
    )

    override fun create(parameters: Parameters) = with(parameters) {
        Decoder.createVideoDecoder(
            location = location, hardwareAccelerationCandidates = hardwareAccelerationCandidates
        )
    }
}