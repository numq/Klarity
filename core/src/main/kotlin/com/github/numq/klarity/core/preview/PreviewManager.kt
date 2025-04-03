package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.hwaccel.HardwareAccelerationFallback
import kotlinx.coroutines.flow.StateFlow

interface PreviewManager {
    val state: StateFlow<PreviewState>

    suspend fun prepare(
        location: String,
        hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.None,
        hardwareAccelerationFallback: HardwareAccelerationFallback = HardwareAccelerationFallback(),
    ): Result<Unit>

    suspend fun release(): Result<Unit>

    suspend fun preview(
        timestampMillis: Long,
        width: Int?,
        height: Int?,
        keyframesOnly: Boolean = true,
    ): Result<Frame.Video.Content?>

    suspend fun close(): Result<Unit>

    companion object {
        fun create(): Result<PreviewManager> = runCatching {
            DefaultPreviewManager(videoDecoderFactory = VideoDecoderFactory())
        }
    }
}