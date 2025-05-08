package com.github.numq.klarity.core.loop.buffer

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.data.Data
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.pool.Pool
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

internal class DefaultBufferLoop(private val pipeline: Pipeline) : BufferLoop {
    private val mutex = Mutex()

    private var job: Job? = null

    override var isBuffering = false

    private suspend fun handleAudioBuffer(
        decoder: Decoder<Media.Audio>, pool: Pool<Data>, buffer: Buffer<Frame>, onTimestamp: suspend (Duration) -> Unit
    ) {
        while (currentCoroutineContext().isActive) {
            val data = pool.acquire().getOrThrow()

            try {
                when (val frame = decoder.decode(data = data).getOrThrow()) {
                    is Frame.Content.Audio -> {
                        currentCoroutineContext().ensureActive()

                        buffer.put(frame).getOrThrow()

                        currentCoroutineContext().ensureActive()

                        onTimestamp(frame.timestamp)
                    }

                    is Frame.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        buffer.put(frame).getOrThrow()

                        break
                    }

                    else -> error("Unsupported frame type: ${frame::class}")
                }
            } catch (t: Throwable) {
                pool.release(item = data).getOrThrow()

                throw t
            }
        }
    }

    private suspend fun handleVideoBuffer(
        decoder: Decoder<Media.Video>, pool: Pool<Data>, buffer: Buffer<Frame>, onTimestamp: suspend (Duration) -> Unit
    ) {
        while (currentCoroutineContext().isActive) {
            val data = pool.acquire().getOrThrow()

            try {
                when (val frame = decoder.decode(data = data).getOrThrow()) {
                    is Frame.Content.Video -> {
                        currentCoroutineContext().ensureActive()

                        buffer.put(frame).getOrThrow()

                        currentCoroutineContext().ensureActive()

                        onTimestamp(frame.timestamp)
                    }

                    is Frame.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        buffer.put(frame).getOrThrow()

                        break
                    }

                    else -> error("Unsupported frame type: ${frame::class}")
                }
            } catch (t: Throwable) {
                pool.release(item = data).getOrThrow()

                throw t
            }
        }
    }

    override suspend fun start(
        coroutineScope: CoroutineScope,
        onException: suspend (BufferLoopException) -> Unit,
        onTimestamp: suspend (Duration) -> Unit,
        onEndOfMedia: suspend () -> Unit,
    ) = mutex.withLock {
        runCatching {
            check(!isBuffering) { "Unable to start buffer loop, call stop first." }

            require(job == null) { "Unable to start buffer loop" }

            isBuffering = true

            val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
                coroutineScope.launch {
                    onException(BufferLoopException(exception))
                }
            }

            job = coroutineScope.launch(context = coroutineExceptionHandler) {
                try {
                    when (pipeline) {
                        is Pipeline.Audio -> with(pipeline) {
                            handleAudioBuffer(
                                decoder = decoder, pool = pool, buffer = buffer, onTimestamp = onTimestamp
                            )
                        }

                        is Pipeline.Video -> with(pipeline) {
                            handleVideoBuffer(
                                decoder = decoder, pool = pool, buffer = buffer, onTimestamp = onTimestamp
                            )
                        }

                        is Pipeline.AudioVideo -> with(pipeline) {
                            var lastFrameTimestamp = Duration.ZERO

                            val audioJob = launch {
                                handleAudioBuffer(
                                    decoder = audioDecoder,
                                    pool = audioPool,
                                    buffer = audioBuffer,
                                    onTimestamp = { frameTimestamp ->
                                        if (frameTimestamp > lastFrameTimestamp) {
                                            onTimestamp(frameTimestamp)

                                            lastFrameTimestamp = frameTimestamp
                                        }
                                    })
                            }

                            val videoJob = launch {
                                handleVideoBuffer(
                                    decoder = videoDecoder,
                                    pool = videoPool,
                                    buffer = videoBuffer,
                                    onTimestamp = { frameTimestamp ->
                                        if (frameTimestamp > lastFrameTimestamp) {
                                            onTimestamp(frameTimestamp)

                                            lastFrameTimestamp = frameTimestamp
                                        }
                                    })
                            }

                            joinAll(audioJob, videoJob)
                        }
                    }

                    ensureActive()

                    onEndOfMedia()
                } finally {
                    isBuffering = false
                }
            }
        }
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            try {
                job?.cancelAndJoin()
                job = null
            } finally {
                isBuffering = false
            }
        }
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            try {
                job?.cancel()
                job = null
            } finally {
                isBuffering = false
            }
        }
    }
}