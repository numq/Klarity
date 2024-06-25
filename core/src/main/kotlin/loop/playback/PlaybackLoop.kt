package loop.playback

import clock.Clock
import kotlinx.coroutines.flow.StateFlow
import loop.buffer.BufferLoop
import pipeline.Pipeline

interface PlaybackLoop {
    val timestamp: StateFlow<Long>
    suspend fun start(endOfMedia: suspend () -> Unit): Result<Unit>
    suspend fun stop(): Result<Unit>
    fun close()

    companion object {
        internal fun create(
            clock: Clock,
            bufferLoop: BufferLoop,
            pipeline: Pipeline,
        ): Result<PlaybackLoop> = runCatching {
            DefaultPlaybackLoop(
                clock = clock,
                bufferLoop = bufferLoop,
                pipeline = pipeline
            )
        }
    }
}