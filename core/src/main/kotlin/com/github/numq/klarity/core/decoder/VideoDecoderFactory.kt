package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.media.Media

class VideoDecoderFactory : Factory<VideoDecoderFactory.Parameters, Decoder<Media.Video>> {
    data class Parameters(
        val location: String,
        val framePoolCapacity: Int,
        val width: Int?,
        val height: Int?,
        val hardwareAccelerationCandidates: List<HardwareAcceleration>?,
    )

    override fun create(parameters: Parameters) = with(parameters) {
        Decoder.createVideoDecoder(
            location = location,
            framePoolCapacity = framePoolCapacity,
            width = width,
            height = height,
            hardwareAccelerationCandidates = hardwareAccelerationCandidates
        )
    }
}