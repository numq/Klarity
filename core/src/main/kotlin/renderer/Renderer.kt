package renderer

import frame.Frame
import kotlinx.coroutines.flow.StateFlow

interface Renderer {
    val width: Int
    val height: Int
    val frameRate: Double
    val preview: Frame.Video.Content?
    val frame: StateFlow<Frame.Video.Content?>
    val playbackSpeedFactor: StateFlow<Float>
    suspend fun setPlaybackSpeed(factor: Float): Result<Unit>
    suspend fun draw(frame: Frame.Video.Content): Result<Unit>
    suspend fun reset(): Result<Unit>

    companion object {
        internal fun create(
            width: Int,
            height: Int,
            frameRate: Double,
            preview: Frame.Video.Content?,
        ): Result<Renderer> = runCatching {
            DefaultRenderer(width = width, height = height, frameRate = frameRate, preview = preview)
        }
    }
}