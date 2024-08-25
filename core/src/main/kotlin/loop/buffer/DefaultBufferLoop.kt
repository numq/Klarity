package loop.buffer

import buffer.Buffer
import decoder.Decoder
import frame.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pipeline.Pipeline
import timestamp.Timestamp
import java.util.concurrent.atomic.AtomicBoolean

internal class DefaultBufferLoop(
    private val pipeline: Pipeline,
) : BufferLoop {
    private val mutex = Mutex()

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var job: Job? = null

    override val isBuffering = AtomicBoolean(false)

    override val isWaiting = AtomicBoolean(true)

    private suspend fun handleAudioBuffer(
        decoder: Decoder<Frame.Audio>,
        buffer: Buffer<Frame.Audio>,
        onTimestamp: suspend (Timestamp) -> Unit,
        onWaiting: suspend () -> Unit,
    ) = with(currentCoroutineContext()) {
        isWaiting.set(true)

        while (isActive) {
            when (val frame = decoder.nextFrame().getOrNull()) {
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
                            ensureActive()

                            buffer.push(frame)

                            ensureActive()

                            onTimestamp(Timestamp(micros = frame.timestampMicros))
                        }

                        is Frame.Audio.EndOfStream -> {
                            ensureActive()

                            buffer.push(frame)

                            break
                        }
                    }
                }
            }
        }
    }

    private suspend fun handleVideoBuffer(
        decoder: Decoder<Frame.Video>,
        buffer: Buffer<Frame.Video>,
        onTimestamp: suspend (Timestamp) -> Unit,
        onWaiting: suspend () -> Unit,
    ) = with(currentCoroutineContext()) {
        isWaiting.set(true)

        while (isActive) {
            when (val frame = decoder.nextFrame().getOrNull()) {
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
                            ensureActive()

                            buffer.push(frame)

                            ensureActive()

                            onTimestamp(Timestamp(micros = frame.timestampMicros))
                        }

                        is Frame.Video.EndOfStream -> {
                            ensureActive()

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

            job = coroutineScope.launch(context = CoroutineExceptionHandler { _, throwable ->
                if (throwable !is CancellationException) throw throwable
            }) {
                isBuffering.set(true)

                when (pipeline) {
                    is Pipeline.Media -> with(pipeline) {
                        joinAll(launch(context = CoroutineExceptionHandler { _, throwable ->
                            if (throwable !is CancellationException) throw throwable
                        }) {
                            handleAudioBuffer(
                                decoder = audioDecoder,
                                buffer = audioBuffer,
                                onTimestamp = onTimestamp,
                                onWaiting = onWaiting
                            )
                        }, launch(context = CoroutineExceptionHandler { _, throwable ->
                            if (throwable !is CancellationException) throw throwable
                        }) {
                            handleVideoBuffer(
                                decoder = videoDecoder,
                                buffer = videoBuffer,
                                onTimestamp = onTimestamp,
                                onWaiting = onWaiting
                            )
                        })
                    }

                    is Pipeline.Audio -> with(pipeline) {
                        handleAudioBuffer(
                            decoder = decoder, buffer = buffer, onTimestamp = onTimestamp, onWaiting = onWaiting
                        )
                    }

                    is Pipeline.Video -> with(pipeline) {
                        handleVideoBuffer(
                            decoder = decoder, buffer = buffer, onTimestamp = onTimestamp, onWaiting = onWaiting
                        )
                    }
                }

                isBuffering.set(false)

                ensureActive()

                endOfMedia()
            }
        }.onFailure {
            isBuffering.set(false)
        }
    }

    override suspend fun stop() = mutex.withLock {
        val result = runCatching {
            job?.cancelAndJoin()
            job = null

            when (pipeline) {
                is Pipeline.Media -> {
                    pipeline.audioBuffer.flush().getOrThrow()

                    pipeline.videoBuffer.flush().getOrThrow()
                }

                is Pipeline.Audio -> pipeline.buffer.flush().getOrThrow()

                is Pipeline.Video -> pipeline.buffer.flush().getOrThrow()
            }
        }

        isBuffering.set(false)

        isWaiting.set(false)

        result
    }

    override fun close() = runCatching { coroutineScope.cancel() }.getOrDefault(Unit)
}