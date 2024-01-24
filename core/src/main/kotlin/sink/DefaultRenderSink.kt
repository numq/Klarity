package sink

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

internal class DefaultRenderSink : RenderSink {
    private var _pixels = MutableStateFlow<ByteArray?>(null)

    override val pixels: StateFlow<ByteArray?> = _pixels.asStateFlow()

    override fun draw(pixels: ByteArray) = _pixels.tryEmit(pixels)

    override fun erase() = _pixels.tryEmit(null)
}