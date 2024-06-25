package renderer

import frame.Frame
import kotlinx.coroutines.flow.StateFlow

interface Renderer : AutoCloseable {
    val width: Int
    val height: Int
    val preview: Frame.Video?
    val frame: StateFlow<Frame.Video.Content?>
    val playbackSpeedFactor: Float
    suspend fun setPlaybackSpeed(factor: Float): Result<Unit>
    suspend fun draw(frame: Frame.Video.Content): Result<Unit>
    suspend fun reset(): Result<Unit>

    companion object {
        internal fun create(width: Int, height: Int, preview: Frame.Video.Content?): Result<Renderer> = runCatching {
            DefaultRenderer(width = width, height = height, preview = preview)
        }
    }
}