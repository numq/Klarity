package loop.playback

import kotlinx.coroutines.flow.StateFlow
import loop.buffer.BufferLoop
import pipeline.Pipeline
import timestamp.Timestamp

interface PlaybackLoop {
    val timestamp: StateFlow<Timestamp>
    suspend fun start(endOfMedia: suspend () -> Unit): Result<Unit>
    suspend fun stop(resetTime: Boolean): Result<Unit>
    suspend fun seekTo(timestamp: Timestamp): Result<Unit>
    fun close()

    companion object {
        internal fun create(bufferLoop: BufferLoop, pipeline: Pipeline): Result<PlaybackLoop> = runCatching {
            DefaultPlaybackLoop(bufferLoop = bufferLoop, pipeline = pipeline)
        }
    }
}