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
import kotlin.time.Duration.Companion.microseconds

internal class DefaultBufferLoop(private val pipeline: Pipeline) : BufferLoop {
    private val mutex = Mutex()

    private var job: Job? = null

    override var isBuffering = false

    private suspend fun handleAudioBuffer(
        decoder: Decoder<Media.Audio, Frame.Audio>, buffer: Buffer<Frame.Audio>, onTimestamp: suspend (Duration) -> Unit
    ) {
        while (currentCoroutineContext().isActive) {
            val frame = decoder.decode().getOrThrow()

            try {
                when (frame) {
                    is Frame.Audio.Content -> {
                        currentCoroutineContext().ensureActive()

                        buffer.put(frame).getOrThrow()

                        currentCoroutineContext().ensureActive()

                        onTimestamp(frame.timestamp)
                    }

                    is Frame.Audio.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        buffer.put(frame).getOrThrow()

                        break
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.IO) { frame.close() }
            }

            delay(500.microseconds)
        }
    }

    private suspend fun handleVideoBuffer(
        decoder: Decoder<Media.Video, Frame.Video>, buffer: Buffer<Frame.Video>, onTimestamp: suspend (Duration) -> Unit
    ) {
        while (currentCoroutineContext().isActive) {
            val frame = decoder.decode().getOrThrow()

            try {
                when (frame) {
                    is Frame.Video.Content -> {
                        currentCoroutineContext().ensureActive()

                        buffer.put(frame).getOrThrow()

                        currentCoroutineContext().ensureActive()

                        onTimestamp(frame.timestamp)
                    }

                    is Frame.Video.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        buffer.put(frame).getOrThrow()

                        break
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.IO) { frame.close() }
            }

            delay(500.microseconds)
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