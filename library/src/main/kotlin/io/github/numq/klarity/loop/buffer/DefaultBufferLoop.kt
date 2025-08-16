package io.github.numq.klarity.loop.buffer

import io.github.numq.klarity.buffer.Buffer
import io.github.numq.klarity.decoder.AudioDecoder
import io.github.numq.klarity.decoder.VideoDecoder
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.pipeline.Pipeline
import io.github.numq.klarity.pool.Pool
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.Data
import kotlin.time.Duration

internal class DefaultBufferLoop(private val pipeline: Pipeline) : BufferLoop {
    private val mutex = Mutex()

    private var job: Job? = null

    private suspend fun handleAudioBuffer(
        decoder: AudioDecoder,
        buffer: Buffer<Frame>,
        onTimestamp: suspend (Duration) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            when (val frame = decoder.decodeAudio().getOrThrow()) {
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
        decoder: VideoDecoder,
        pool: Pool<Data>,
        buffer: Buffer<Frame>,
        onTimestamp: suspend (Duration) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            val data = pool.acquire().getOrThrow()

            try {
                currentCoroutineContext().ensureActive()

                when (val frame = decoder.decodeVideo(data = data).getOrThrow()) {
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
            check(job == null) { "Unable to start buffer loop, call stop first." }

            val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
                coroutineScope.launch {
                    onException(BufferLoopException(exception))
                }
            }

            job = coroutineScope.launch(context = coroutineExceptionHandler) {
                with(pipeline) {
                    when (this) {
                        is Pipeline.Audio -> handleAudioBuffer(
                            decoder = decoder as AudioDecoder, buffer = buffer, onTimestamp = onTimestamp
                        )

                        is Pipeline.Video -> handleVideoBuffer(
                            decoder = decoder as VideoDecoder, pool = pool, buffer = buffer, onTimestamp = onTimestamp
                        )

                        is Pipeline.AudioVideo -> {
                            var lastFrameTimestamp = Duration.ZERO

                            val audioJob = launch {
                                handleAudioBuffer(
                                    decoder = audioDecoder as AudioDecoder,
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
                                    decoder = videoDecoder as VideoDecoder,
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

                    onTimestamp(media.duration)

                    onEndOfMedia()
                }
            }
        }
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            try {
                job?.cancelAndJoin()
            } catch (_: CancellationException) {

            } finally {
                job = null
            }

            Unit
        }
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            job?.cancel()

            job = null
        }
    }
}