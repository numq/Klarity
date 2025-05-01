package com.github.numq.klarity.core.loop.buffer

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

internal class DefaultBufferLoop(private val pipeline: Pipeline) : BufferLoop {
    private val mutex = Mutex()

    private var job: Job? = null

    override var isBuffering = false

    private suspend fun handleAudioBuffer(
        decoder: Decoder<Media.Audio>, buffer: Buffer<Frame>, onTimestamp: suspend (Duration) -> Unit
    ) {
        while (currentCoroutineContext().isActive) {
            when (val frame = decoder.decode().getOrThrow()) {
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
        }
    }

    private suspend fun handleVideoBuffer(
        decoder: Decoder<Media.Video>, buffer: Buffer<Frame>, onTimestamp: suspend (Duration) -> Unit
    ) {
        while (currentCoroutineContext().isActive) {
            when (val frame = decoder.decode().getOrThrow()) {
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
                                decoder = decoder, buffer = buffer, onTimestamp = onTimestamp
                            )
                        }

                        is Pipeline.Video -> with(pipeline) {
                            handleVideoBuffer(
                                decoder = decoder, buffer = buffer, onTimestamp = onTimestamp
                            )
                        }

                        is Pipeline.AudioVideo -> with(pipeline) {
                            var lastFrameTimestamp = Duration.ZERO

                            joinAll(launch {
                                handleAudioBuffer(
                                    decoder = audioDecoder, buffer = audioBuffer, onTimestamp = { frameTimestamp ->
                                        if (frameTimestamp > lastFrameTimestamp) {
                                            onTimestamp(frameTimestamp)

                                            lastFrameTimestamp = frameTimestamp
                                        }
                                    })
                            }, launch {
                                handleVideoBuffer(
                                    decoder = videoDecoder, buffer = videoBuffer, onTimestamp = { frameTimestamp ->
                                        if (frameTimestamp > lastFrameTimestamp) {
                                            onTimestamp(frameTimestamp)

                                            lastFrameTimestamp = frameTimestamp
                                        }
                                    })
                            })
                        }
                    }

                    ensureActive()

                    onEndOfMedia()
                } catch (t: Throwable) {
                    throw t
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
            } catch (t: Throwable) {
                throw t
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
            } catch (t: Throwable) {
                throw t
            } finally {
                isBuffering = false
            }
        }
    }
}