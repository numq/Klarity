package preview

import decoder.VideoDecoderFactory
import frame.Frame
import kotlinx.coroutines.flow.StateFlow

interface PreviewManager : AutoCloseable {
    val state: StateFlow<PreviewState>
    suspend fun prepare(location: String): Result<Unit>
    suspend fun release(): Result<Unit>
    suspend fun preview(
        timestampMillis: Long,
        width: Int?,
        height: Int?,
        keyframesOnly: Boolean = false,
    ): Result<Frame.Video.Content?>

    companion object {
        fun create(): Result<PreviewManager> = runCatching {
            DefaultPreviewManager(videoDecoderFactory = VideoDecoderFactory())
        }
    }
}