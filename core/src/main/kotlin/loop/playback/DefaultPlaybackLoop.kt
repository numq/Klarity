package loop.playback

import buffer.Buffer
import clock.Clock
import frame.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import loop.buffer.BufferLoop
import pipeline.Pipeline
import renderer.Renderer
import sampler.Sampler
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal class DefaultPlaybackLoop(
    private val clock: Clock,
    private val bufferLoop: BufferLoop,
    private val pipeline: Pipeline,
) : PlaybackLoop {
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    @Volatile
    private var job: Job? = null

    @Volatile
    private var initialAudioLatency = Duration.INFINITE

    @Volatile
    private var initialVideoLatency = Duration.INFINITE

    private suspend fun Pipeline.waitForTimestamp() {
        while (currentCoroutineContext().isActive) {
            val timestampMicros = when (this) {
                is Pipeline.Media -> audioBuffer.peek().getOrThrow()?.timestampMicros ?: videoBuffer.peek()
                    .getOrThrow()?.timestampMicros

                is Pipeline.Audio -> buffer.peek().getOrThrow()?.timestampMicros

                is Pipeline.Video -> buffer.peek().getOrThrow()?.timestampMicros
            } ?: continue

            clock.start(timestampMicros)

            break
        }
    }

    private suspend fun handleAudioPlayback(
        buffer: Buffer<Frame.Audio>,
        sampler: Sampler,
    ) {
        while (currentCoroutineContext().isActive) {
            when (val frame = buffer.peek().getOrNull()) {
                null -> if (bufferLoop.isBuffering.get()) yield() else break

                is Frame.Audio.Content -> {
                    val elapsedTime = clock.getElapsedMicros().microseconds.also {
                        if (initialAudioLatency == Duration.INFINITE) initialAudioLatency = it
                    }.minus(initialAudioLatency)

                    check(initialAudioLatency.isFinite()) { "Unable to initialize latency" }

                    if (frame.timestampMicros.microseconds <= elapsedTime) {
                        var isQueued = false

                        do {
                            sampler.play(bytes = frame.bytes).onSuccess { queued ->
                                audioTimestamps.tryEmit(
                                    frame.timestampMicros.microseconds.inWholeMilliseconds.minus(
                                        sampler.getCurrentTime().getOrNull()?.toLong() ?: 0L
                                    )
                                )

                                isQueued = queued
                            }.getOrThrow()
                        } while (!isQueued)

                        buffer.poll().getOrThrow()
                    }
                }

                is Frame.Audio.EndOfMedia -> {
                    audioTimestamps.tryEmit(frame.timestampMicros.microseconds.inWholeMilliseconds)
                    break
                }
            }
        }
    }

    private suspend fun handleVideoPlayback(
        buffer: Buffer<Frame.Video>,
        renderer: Renderer,
    ) {
        while (currentCoroutineContext().isActive) {
            when (val frame = buffer.peek().getOrNull()) {
                null -> if (bufferLoop.isBuffering.get()) yield() else break

                is Frame.Video.Content -> {
                    val elapsedTime = clock.getElapsedMicros().microseconds.also {
                        if (initialVideoLatency == Duration.INFINITE) initialVideoLatency = it
                    }.minus(initialVideoLatency)

                    check(initialVideoLatency.isFinite()) { "Unable to initialize latency" }

                    if (frame.timestampMicros.microseconds <= elapsedTime) {
                        renderer.draw(frame).onSuccess {
                            videoTimestamps.tryEmit(frame.timestampMicros.microseconds.inWholeMilliseconds)
                        }.getOrThrow()

                        buffer.poll().getOrThrow()
                    }
                }

                is Frame.Video.EndOfMedia -> {
                    videoTimestamps.tryEmit(frame.timestampMicros.microseconds.inWholeMilliseconds)
                    break
                }
            }
        }
    }

    private val audioTimestamps =
        MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    private val videoTimestamps =
        MutableSharedFlow<Long>(replay = 1, extraBufferCapacity = 0, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val timestamp = audioTimestamps.combine(videoTimestamps, ::minOf).stateIn(
        scope = coroutineScope, started = SharingStarted.Lazily, initialValue = 0L
    )

    override suspend fun start(endOfMedia: suspend () -> Unit) = runCatching {
        job = coroutineScope.launch(context = CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) throw throwable
        }) {
            with(pipeline) {
                waitForTimestamp()

                when (this) {
                    is Pipeline.Media -> joinAll(launch { handleAudioPlayback(audioBuffer, sampler) },
                        launch { handleVideoPlayback(videoBuffer, renderer) })

                    is Pipeline.Audio -> handleAudioPlayback(buffer, sampler)

                    is Pipeline.Video -> handleVideoPlayback(buffer, renderer)
                }
            }

            endOfMedia()

            job = null
        }
    }

    override suspend fun stop() = runCatching {
        job?.cancelAndJoin()
        job = null
    }

    override fun close() = runCatching { coroutineScope.cancel() }.getOrDefault(Unit)
}