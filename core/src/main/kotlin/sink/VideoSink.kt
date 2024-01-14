package sink

import frame.DecodedFrame
import kotlinx.coroutines.flow.StateFlow

interface VideoSink : AutoCloseable {

    val videoFrame: StateFlow<DecodedFrame.Video?>
    fun updateVideoFrame(frame: DecodedFrame.Video): Boolean
    fun disposeVideoFrame(): Boolean

    companion object {
        fun create(): VideoSink = DefaultVideoSink()
    }
}