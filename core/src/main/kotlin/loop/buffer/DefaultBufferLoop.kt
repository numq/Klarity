package loop.buffer

import buffer.Buffer
import decoder.Decoder
import frame.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import pipeline.Pipeline
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.microseconds

internal class DefaultBufferLoop(
    private val pipeline: Pipeline,
) : BufferLoop {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var job: Job? = null

    private val audioTimestamps = MutableSharedFlow<Long>(replay = 1)

    private val videoTimestamps = MutableSharedFlow<Long>(replay = 1)

    override val isBuffering = AtomicBoolean(false)

    override val isWaiting = AtomicBoolean(false)

    override val timestamp = audioTimestamps.combine(videoTimestamps, ::minOf).stateIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily,
        initialValue = 0L
    )

    private suspend fun handleAudioBuffer(
        decoder: Decoder<Frame.Audio>,
        buffer: Buffer<Frame.Audio>,
        onWaiting: suspend () -> Unit,
    ) = with(currentCoroutineContext()) {
        while (isActive) {
            when (val frame = decoder.nextFrame().getOrNull()) {
                null -> {
                    isWaiting.set(true)

                    onWaiting()

                    Thread.onSpinWait()
                }

                else -> {
                    isWaiting.set(false)

                    when (frame) {
                        is Frame.Audio.Content -> {
                            buffer.push(frame)
                            audioTimestamps.tryEmit(frame.timestampMicros.microseconds.inWholeMilliseconds)
                        }


                        is Frame.Audio.EndOfMedia -> {
                            buffer.push(frame)
                            audioTimestamps.tryEmit(frame.timestampMicros.microseconds.inWholeMilliseconds)
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
    ) = with(currentCoroutineContext()) {
        while (isActive) {
            when (val frame = decoder.nextFrame().getOrNull()) {
                null -> {
                    isWaiting.set(true)

                    onWaiting()

                    Thread.onSpinWait()
                }

                else -> {
                    isWaiting.set(false)

                    when (frame) {
                        is Frame.Video.Content -> {
                            buffer.push(frame)
                            videoTimestamps.tryEmit(frame.timestampMicros.microseconds.inWholeMilliseconds)
                        }

                        is Frame.Video.EndOfMedia -> {
                            buffer.push(frame)
                            videoTimestamps.tryEmit(frame.timestampMicros.microseconds.inWholeMilliseconds)
                            break
                        }
                    }
                }
            }
        }
    }

    override suspend fun start(
        onWaiting: suspend () -> Unit,
        endOfMedia: suspend () -> Unit,
    ) = runCatching {
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

            endOfMedia()

            isBuffering.set(false)

            job = null
        }
    }.onFailure {
        isBuffering.set(false)
    }

    override suspend fun stop() = runCatching {
        isBuffering.set(false)

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

    override fun close() = runCatching { coroutineScope.cancel() }.getOrDefault(Unit)
}