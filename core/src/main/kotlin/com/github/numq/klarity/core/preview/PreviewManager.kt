package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import kotlinx.coroutines.flow.StateFlow

interface PreviewManager : AutoCloseable {
    val state: StateFlow<PreviewState>
    suspend fun prepare(location: String): Result<Unit>
    suspend fun release(): Result<Unit>
    suspend fun preview(
        timestampMillis: Long,
        width: Int?,
        height: Int?,
        keyframesOnly: Boolean = true,
    ): Result<Frame.Video.Content?>

    companion object {
        fun create(hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.NONE): Result<PreviewManager> =
            runCatching {
                DefaultPreviewManager(
                    hardwareAcceleration = hardwareAcceleration,
                    videoDecoderFactory = VideoDecoderFactory()
                )
            }
    }
}