package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import kotlinx.coroutines.flow.StateFlow

interface PreviewManager {
    val state: StateFlow<PreviewState>

    suspend fun prepare(
        location: String,
        width: Int? = null,
        height: Int? = null,
        hardwareAccelerationCandidates: List<HardwareAcceleration> = emptyList(),
    ): Result<Unit>

    suspend fun release(): Result<Unit>

    suspend fun preview(timestampMillis: Long, keyframesOnly: Boolean = true): Result<Frame.Video.Content?>

    suspend fun close(): Result<Unit>

    companion object {
        fun create(): Result<PreviewManager> = runCatching {
            DefaultPreviewManager(videoDecoderFactory = VideoDecoderFactory())
        }
    }
}