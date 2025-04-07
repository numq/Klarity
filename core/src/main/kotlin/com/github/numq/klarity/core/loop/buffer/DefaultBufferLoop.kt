package com.github.numq.klarity.core.loop.buffer

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.timestamp.Timestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean

internal class DefaultBufferLoop(
    private val pipeline: Pipeline,
) : BufferLoop {
    private val mutex = Mutex()

    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    private val coroutineScope = CoroutineScope(coroutineContext)

    private var job: Job? = null

    override val isBuffering = AtomicBoolean(false)

    override val isWaiting = AtomicBoolean(true)

    private suspend fun handleAudioBuffer(
        decoder: Decoder<Media.Audio, Frame.Audio>,
        buffer: Buffer<Frame.Audio>,
        onTimestamp: suspend (Timestamp) -> Unit,
        onWaiting: suspend () -> Unit,
    ) {
        isWaiting.set(true)

        while (currentCoroutineContext().isActive && isBuffering.get()) {
            when (val frame = decoder.decode(width = null, height = null).getOrNull()) {
                null -> {
                    isWaiting.set(true)

                    onWaiting()

                    yield()

                    continue
                }

                else -> {
                    isWaiting.set(false)

                    when (frame) {
                        is Frame.Audio.Content -> {
                            buffer.push(frame)

                            currentCoroutineContext().ensureActive()

                            onTimestamp(Timestamp(micros = frame.timestampMicros))
                        }

                        is Frame.Audio.EndOfStream -> {
                            buffer.push(frame)

                            break
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleVideoBuffer(
        decoder: Decoder<Media.Video, Frame.Video>,
        buffer: Buffer<Frame.Video>,
        onTimestamp: suspend (Timestamp) -> Unit,
        onWaiting: suspend () -> Unit,
    ) {
        isWaiting.set(true)

        while (currentCoroutineContext().isActive && isBuffering.get()) {
            when (val frame = decoder.decode(width = null, height = null).getOrNull()) {
                null -> {
                    isWaiting.set(true)

                    onWaiting()

                    yield()

                    continue
                }

                else -> {
                    isWaiting.set(false)

                    when (frame) {
                        is Frame.Video.Content -> {
                            buffer.push(frame)

                            currentCoroutineContext().ensureActive()

                            onTimestamp(Timestamp(micros = frame.timestampMicros))
                        }

                        is Frame.Video.EndOfStream -> {
                            buffer.push(frame)

                            break
                        }
                    }
                }
            }
        }
    }

    override suspend fun start(
        onTimestamp: suspend (Timestamp) -> Unit,
        onWaiting: suspend () -> Unit,
        endOfMedia: suspend () -> Unit,
    ) = mutex.withLock {
        runCatching {
            check(!isBuffering.get()) { "Unable to start buffer loop, call stop first." }

            require(job == null) { "Unable to start buffer loop" }

            isBuffering.set(true)

            job = coroutineScope.launch {
                try {
                    when (pipeline) {
                        is Pipeline.AudioVideo -> {
                            val audioTimestamps = MutableSharedFlow<Timestamp>()
                            val videoTimestamps = MutableSharedFlow<Timestamp>()

                            val timestampJob = when {
                                ((pipeline.media as? Media.AudioVideo)?.videoFormat?.frameRate ?: 0.0) > 0.0 -> {
                                    audioTimestamps.combine(videoTimestamps) { a, v ->
                                        if (a.micros >= v.micros) a else v
                                    }
                                }

                                else -> merge(audioTimestamps, videoTimestamps)
                            }.onEach(onTimestamp).launchIn(this@launch)

                            with(pipeline) {
                                joinAll(
                                    launch {
                                        handleAudioBuffer(
                                            decoder = audioDecoder,
                                            buffer = audioBuffer,
                                            onTimestamp = { timestamp ->
                                                audioTimestamps.emit(timestamp)
                                            },
                                            onWaiting = onWaiting
                                        )
                                    },
                                    launch {
                                        handleVideoBuffer(
                                            decoder = videoDecoder,
                                            buffer = videoBuffer,
                                            onTimestamp = { timestamp ->
                                                videoTimestamps.emit(timestamp)
                                            },
                                            onWaiting = onWaiting
                                        )
                                    }
                                )
                            }

                            timestampJob.cancelAndJoin()
                        }

                        is Pipeline.Audio -> with(pipeline) {
                            handleAudioBuffer(
                                decoder = decoder,
                                buffer = buffer,
                                onTimestamp = onTimestamp,
                                onWaiting = onWaiting
                            )
                        }

                        is Pipeline.Video -> with(pipeline) {
                            handleVideoBuffer(
                                decoder = decoder,
                                buffer = buffer,
                                onTimestamp = onTimestamp,
                                onWaiting = onWaiting
                            )
                        }
                    }

                    ensureActive()

                    endOfMedia()
                } catch (t: Throwable) {
                    throw t
                } finally {
                    isBuffering.set(false)
                }
            }
        }
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            job?.cancel()
            job = null

            isWaiting.set(false)

            isBuffering.set(false)
        }
    }

    override suspend fun close() = runCatching {
        stop().getOrThrow()

        coroutineScope.cancel()
    }
}