package loop.playback

import loop.buffer.BufferLoop
import pipeline.Pipeline
import timestamp.Timestamp

interface PlaybackLoop : AutoCloseable {
    suspend fun start(
        onTimestamp: suspend (Timestamp) -> Unit,
        endOfMedia: suspend () -> Unit,
    ): Result<Unit>

    suspend fun stop(): Result<Unit>

    companion object {
        internal fun create(bufferLoop: BufferLoop, pipeline: Pipeline): Result<PlaybackLoop> = runCatching {
            DefaultPlaybackLoop(bufferLoop = bufferLoop, pipeline = pipeline)
        }
    }
}