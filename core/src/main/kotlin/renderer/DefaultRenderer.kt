package renderer

import frame.Frame
import kotlinx.coroutines.flow.MutableStateFlow

internal class DefaultRenderer(
    override val width: Int,
    override val height: Int,
    override val frameRate: Double,
    override val preview: Frame.Video.Content?,
) : Renderer {
    override val frame = MutableStateFlow(preview)

    override var playbackSpeedFactor = MutableStateFlow(1f)

    override suspend fun setPlaybackSpeed(factor: Float) = runCatching {
        require(factor > 0f) { "Speed factor should be positive" }

        playbackSpeedFactor.emit(factor)
    }

    override suspend fun draw(frame: Frame.Video.Content) = runCatching {
        this@DefaultRenderer.frame.emit(frame)
    }

    override suspend fun reset() = runCatching {
        frame.emit(preview)
    }
}