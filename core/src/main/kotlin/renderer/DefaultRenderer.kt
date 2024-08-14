package renderer

import frame.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

internal class DefaultRenderer(
    override val width: Int,
    override val height: Int,
    override val frameRate: Double,
    override val preview: Frame.Video.Content?,
) : Renderer {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    override val frame = MutableStateFlow<Frame.Video.Content?>(null)

    @Volatile
    override var playbackSpeedFactor: Float = 1.0f

    override suspend fun setPlaybackSpeed(factor: Float) = runCatching {
        require(factor > 0f) { "Speed factor should be positive" }

        playbackSpeedFactor = factor
    }

    override suspend fun draw(frame: Frame.Video.Content) = runCatching {
        this@DefaultRenderer.frame.tryEmit(frame)
        Unit
    }

    override suspend fun reset() = runCatching {
        frame.tryEmit(preview)
        Unit
    }

    override fun close() = runCatching { coroutineScope.cancel() }.getOrDefault(Unit)

    init {
        coroutineScope.launch {
            reset()
        }
    }
}