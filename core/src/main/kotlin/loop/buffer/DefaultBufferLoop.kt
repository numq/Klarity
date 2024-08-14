package loop.buffer

import buffer.Buffer
import decoder.Decoder
import frame.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import pipeline.Pipeline
import timestamp.Timestamp
import java.util.concurrent.atomic.AtomicBoolean

internal class DefaultBufferLoop(
    private val pipeline: Pipeline,
) : BufferLoop {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var job: Job? = null

    override val isBuffering = AtomicBoolean(false)
    override val isWaiting = AtomicBoolean(true)

    private val audioTimestamp = MutableStateFlow(Timestamp.ZERO)
    private val videoTimestamp = MutableStateFlow(Timestamp.ZERO)

    private suspend fun setAudioTimestamp(timestamp: Timestamp) {
        if (isBuffering.get() && timestamp.micros in (0L..pipeline.media.durationMicros)) {
            audioTimestamp.emit(timestamp)
        }
    }

    private suspend fun setVideoTimestamp(timestamp: Timestamp) {
        if (isBuffering.get() && timestamp.micros in (0L..pipeline.media.durationMicros)) {
            videoTimestamp.emit(timestamp)
        }
    }

    private suspend fun handleAudioBuffer(
        decoder: Decoder<Frame.Audio>,
        buffer: Buffer<Frame.Audio>,
        onWaiting: suspend () -> Unit,
    ) {
        isWaiting.set(true)

        while (currentCoroutineContext().isActive && isBuffering.get()) {
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
                            buffer.push(frame)

                            setAudioTimestamp(Timestamp(micros = frame.timestampMicros))
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
        decoder: Decoder<Frame.Video>,
        buffer: Buffer<Frame.Video>,
        onWaiting: suspend () -> Unit,
    ) {
        isWaiting.set(true)

        while (currentCoroutineContext().isActive && isBuffering.get()) {
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
                            buffer.push(frame)

                            setVideoTimestamp(Timestamp(micros = frame.timestampMicros))
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

    override val timestamp: StateFlow<Timestamp> = audioTimestamp.combine(videoTimestamp) { audio, video ->
        if (audio.micros >= video.micros) audio else video
    }.stateIn(scope = coroutineScope, started = SharingStarted.Lazily, initialValue = Timestamp.ZERO)

    override suspend fun start(
        onWaiting: suspend () -> Unit,
        endOfMedia: suspend () -> Unit,
    ) = runCatching {
        check(!isBuffering.get()) { "Unable to start buffer loop, call stop first." }

        job = coroutineScope.launch(
            context = CoroutineExceptionHandler { _, throwable ->
                if (throwable !is CancellationException) throw throwable
            }
        ) {
            isBuffering.set(true)

            when (pipeline) {
                is Pipeline.Media -> with(pipeline) {
                    joinAll(
                        launch(
                            context = CoroutineExceptionHandler { _, throwable ->
                                if (throwable !is CancellationException) throw throwable
                            }
                        ) {
                            handleAudioBuffer(
                                decoder = audioDecoder,
                                buffer = audioBuffer,
                                onWaiting = onWaiting
                            )
                        },
                        launch(
                            context = CoroutineExceptionHandler { _, throwable ->
                                if (throwable !is CancellationException) throw throwable
                            }
                        ) {
                            handleVideoBuffer(
                                decoder = videoDecoder,
                                buffer = videoBuffer,
                                onWaiting = onWaiting
                            )
                        }
                    )
                }

                is Pipeline.Audio -> with(pipeline) {
                    handleAudioBuffer(
                        decoder = decoder,
                        buffer = buffer,
                        onWaiting = onWaiting
                    )
                }

                is Pipeline.Video -> with(pipeline) {
                    handleVideoBuffer(
                        decoder = decoder,
                        buffer = buffer,
                        onWaiting = onWaiting
                    )
                }
            }

            isBuffering.set(false)

            endOfMedia()
        }
    }.onFailure {
        isBuffering.set(false)
    }

    override suspend fun stop(resetTime: Boolean) = runCatching {
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

        if (resetTime) {
            audioTimestamp.emit(Timestamp.ZERO)
            videoTimestamp.emit(Timestamp.ZERO)
        }

        isBuffering.set(false)

        isWaiting.set(false)
    }.onFailure {
        isBuffering.set(false)
    }

    override fun close() = runCatching {
        coroutineScope.cancel()
    }.getOrDefault(Unit)
}