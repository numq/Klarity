package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.factory.SuspendFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.media.Media

class VideoDecoderFactory : SuspendFactory<VideoDecoderFactory.Parameters, Decoder<Media.Video, Frame.Video>> {
    data class Parameters(
        val location: String,
        val hardwareAcceleration: HardwareAcceleration,
    )

    override suspend fun create(parameters: Parameters) = with(parameters) {
        Decoder.createVideoDecoder(
            location = location,
            hardwareAcceleration = hardwareAcceleration
        )
    }
}