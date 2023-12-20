package video

import frame.DecodedFrame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.onFailure
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.consumeAsFlow
import kotlinx.coroutines.flow.stateIn

internal class DefaultVideoSink : VideoSink {

    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    private val coroutineScope = CoroutineScope(coroutineContext)

    private var _videoFrames = Channel<DecodedFrame.Video?>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val videoFrame: StateFlow<DecodedFrame.Video?> =
        _videoFrames.consumeAsFlow().stateIn(coroutineScope, SharingStarted.Lazily, null)

    override fun updateVideoFrame(frame: DecodedFrame.Video) =
        _videoFrames.trySend(frame).onFailure { println("Skipping video frame") }.isSuccess

    override fun disposeVideoFrame() =
        _videoFrames.trySend(null).onFailure { println("Unable to dispose video frame") }.isSuccess

    override fun close() {
        coroutineScope.cancel()

        _videoFrames.close()
    }
}