package com.github.numq.klarity.core.loop.buffer

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.timestamp.Timestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

internal class DefaultBufferLoop(
    private val pipeline: Pipeline,
) : BufferLoop {
    private val mutex = Mutex()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var job: Job? = null

    override var isBuffering = false

    private suspend fun handleAudioBuffer(
        decoder: Decoder<Media.Audio, Frame.Audio>,
        buffer: Buffer<Frame.Audio>,
        onTimestamp: suspend (Timestamp) -> Unit
    ) {
        while (currentCoroutineContext().isActive && isBuffering) {
            when (val frame = decoder.decode().getOrThrow()) {
                is Frame.Audio.Content -> {
                    buffer.push(frame).getOrThrow()

                    onTimestamp(Timestamp(micros = frame.timestampMicros))
                }

                is Frame.Audio.EndOfStream -> {
                    buffer.push(frame).getOrThrow()

                    break
                }
            }
        }
    }

    private suspend fun handleVideoBuffer(
        decoder: Decoder<Media.Video, Frame.Video>,
        buffer: Buffer<Frame.Video>,
        onTimestamp: suspend (Timestamp) -> Unit
    ) {
        while (currentCoroutineContext().isActive && isBuffering) {
            when (val frame = decoder.decode().getOrThrow()) {
                is Frame.Video.Content -> {
                    buffer.push(frame).getOrThrow()

                    onTimestamp(Timestamp(micros = frame.timestampMicros))
                }

                is Frame.Video.EndOfStream -> {
                    buffer.push(frame).getOrThrow()

                    break
                }
            }
        }
    }

    override suspend fun start(
        onException: suspend (BufferLoopException) -> Unit,
        onTimestamp: suspend (Timestamp) -> Unit,
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
                                decoder = decoder,
                                buffer = buffer,
                                onTimestamp = onTimestamp
                            )
                        }

                        is Pipeline.Video -> with(pipeline) {
                            handleVideoBuffer(
                                decoder = decoder,
                                buffer = buffer,
                                onTimestamp = onTimestamp
                            )
                        }

                        is Pipeline.AudioVideo -> with(pipeline) {
                            var lastTimestampMicros = 0L

                            joinAll(
                                launch {
                                    handleAudioBuffer(
                                        decoder = audioDecoder,
                                        buffer = audioBuffer,
                                        onTimestamp = { audioTimestamp ->
                                            if (audioTimestamp.micros > lastTimestampMicros) {
                                                onTimestamp(audioTimestamp)

                                                lastTimestampMicros = audioTimestamp.micros
                                            }
                                        }
                                    )
                                },
                                launch {
                                    handleVideoBuffer(
                                        decoder = videoDecoder,
                                        buffer = videoBuffer,
                                        onTimestamp = { videoTimestamp ->
                                            if (videoTimestamp.micros > lastTimestampMicros) {
                                                onTimestamp(videoTimestamp)

                                                lastTimestampMicros = videoTimestamp.micros
                                            }
                                        }
                                    )
                                }
                            )
                        }
                    }

                    currentCoroutineContext().ensureActive()

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
            job?.cancel()
            job = null

            isBuffering = false
        }
    }

    override suspend fun close() = runCatching {
        stop().getOrThrow()

        coroutineScope.cancel()
    }
}