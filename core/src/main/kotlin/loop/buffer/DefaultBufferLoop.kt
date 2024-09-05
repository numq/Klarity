package loop.buffer

import buffer.Buffer
import decoder.Decoder
import frame.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import media.Media
import pipeline.Pipeline
import timestamp.Timestamp
import java.util.concurrent.atomic.AtomicBoolean

internal class DefaultBufferLoop(
    private val pipeline: Pipeline,
) : BufferLoop {
    private val mutex = Mutex()

    private val coroutineScope = CoroutineScope(Dispatchers.Default + Job())

    private var job: Job? = null

    override val isBuffering = AtomicBoolean(false)

    override val isWaiting = AtomicBoolean(true)

    private suspend fun handleAudioBuffer(
        decoder: Decoder<Media.Audio, Frame.Audio>,
        buffer: Buffer<Frame.Audio>,
        onTimestamp: suspend (Timestamp) -> Unit,
        onWaiting: suspend () -> Unit,
    ) = coroutineScope {
        isWaiting.set(true)

        while (isActive && isBuffering.get()) {
            when (val frame = decoder.nextFrame(null, null).getOrNull()) {
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

                            ensureActive()

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
    ) = coroutineScope {
        isWaiting.set(true)

        while (isActive && isBuffering.get()) {
            when (val frame = decoder.nextFrame(null, null).getOrNull()) {
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

                            ensureActive()

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

            job = coroutineScope.launch {
                try {
                    isBuffering.set(true)

                    when (pipeline) {
                        is Pipeline.AudioVideo -> {
                            val audioTimestamps = MutableSharedFlow<Timestamp>()
                            val videoTimestamps = MutableSharedFlow<Timestamp>()

                            val timestampJob = audioTimestamps.combine(videoTimestamps) { a, v ->
                                if (a.micros >= v.micros) a else v
                            }.onEach { timestamp ->
                                onTimestamp(timestamp)
                            }.launchIn(this)

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
        isBuffering.set(false)

        isWaiting.set(false)

        runCatching {
            job?.cancelAndJoin()
            job = null

            when (pipeline) {
                is Pipeline.AudioVideo -> {
                    pipeline.audioBuffer.flush().getOrThrow()

                    pipeline.videoBuffer.flush().getOrThrow()
                }

                is Pipeline.Audio -> pipeline.buffer.flush().getOrThrow()

                is Pipeline.Video -> pipeline.buffer.flush().getOrThrow()
            }
        }
    }

    override fun close() = coroutineScope.coroutineContext.cancelChildren()
}