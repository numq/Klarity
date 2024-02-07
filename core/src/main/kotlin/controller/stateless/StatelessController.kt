package controller.stateless

import kotlinx.coroutines.flow.Flow
import media.Media
import playback.PlaybackEvent
import sink.RenderSink
import synchronizer.PlaybackSynchronizer

interface StatelessController : AutoCloseable {
    companion object {
        fun create(renderSink: RenderSink): StatelessController = DefaultStatelessController(
            synchronizer = PlaybackSynchronizer.create(),
            renderSink = renderSink,
        )
    }

    val renderSink: RenderSink
    val bufferTimestampNanos: Flow<Long>
    val playbackTimestampNanos: Flow<Long>
    val event: Flow<PlaybackEvent>
    suspend fun changeRemoteBufferSizeFactor(value: Int)
    suspend fun snapshot(timestampMillis: Long): ByteArray?
    suspend fun setMuted(state: Boolean): Boolean?
    suspend fun changeVolume(value: Float): Float?
    suspend fun load(media: Media)
    suspend fun unload()
    suspend fun play()
    suspend fun pause()
    suspend fun stop()
    suspend fun seekTo(timestampMillis: Long)
}