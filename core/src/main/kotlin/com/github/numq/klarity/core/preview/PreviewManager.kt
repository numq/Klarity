package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.renderer.RendererFactory

interface PreviewManager {
    val renderer: Renderer

    suspend fun preview(
        timestampMillis: Long,
        debounceMillis: Long = 100L,
        keyframesOnly: Boolean = false,
    ): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        suspend fun create(
            location: String,
            width: Int? = null,
            height: Int? = null,
            hardwareAccelerationCandidates: List<HardwareAcceleration>? = null,
        ): Result<PreviewManager> = runCatching {
            VideoDecoderFactory().create(
                parameters = VideoDecoderFactory.Parameters(
                    location = location,
                    width = width,
                    height = height,
                    frameRate = null,
                    hardwareAccelerationCandidates = hardwareAccelerationCandidates
                )
            ).mapCatching { decoder ->
                DefaultPreviewManager(
                    videoDecoder = decoder, renderer = RendererFactory().create(
                        parameters = RendererFactory.Parameters(
                            format = decoder.media.format, preview = null
                        )
                    ).getOrThrow()
                )
            }.getOrThrow()
        }
    }
}