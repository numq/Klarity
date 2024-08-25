package loop.playback

import buffer.Buffer
import frame.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import loop.buffer.BufferLoop
import pipeline.Pipeline
import renderer.Renderer
import sampler.Sampler
import timestamp.Timestamp
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

internal class DefaultPlaybackLoop(
    private val bufferLoop: BufferLoop,
    private val pipeline: Pipeline,
) : PlaybackLoop {
    private val mutex = Mutex()

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var job: Job? = null

    private val isPlaying = AtomicBoolean(false)

    private suspend fun handleMediaPlayback(
        audioBuffer: Buffer<Frame.Audio>,
        videoBuffer: Buffer<Frame.Video>,
        sampler: Sampler,
        renderer: Renderer,
        onTimestamp: suspend (Timestamp) -> Unit,
    ) = with(currentCoroutineContext()) {
        val lastAudioTimestamp = AtomicReference(Duration.INFINITE)

        val audioJob = CoroutineScope(this).launch {
            while (isActive) {
                if (bufferLoop.isWaiting.get()) {
                    yield()

                    continue
                }

                when (val frame = audioBuffer.poll().getOrThrow()) {
                    is Frame.Audio.Content -> {
                        ensureActive()

                        lastAudioTimestamp.set(frame.timestampMicros.microseconds)

                        ensureActive()

                        onTimestamp(Timestamp(micros = frame.timestampMicros))

                        ensureActive()

                        sampler.play(bytes = frame.bytes).getOrThrow()
                    }

                    is Frame.Audio.EndOfStream -> break
                }
            }
        }

        val videoJob = CoroutineScope(this).launch {
            while (isActive) {
                if (bufferLoop.isWaiting.get()) {
                    yield()

                    continue
                }

                when (val frame = videoBuffer.peek().getOrThrow()) {
                    null -> {
                        yield()

                        continue
                    }

                    is Frame.Video.Content -> if (lastAudioTimestamp.get().isFinite()) {
                        if (audioJob.isCompleted || frame.timestampMicros.microseconds <= lastAudioTimestamp.get()) {
                            ensureActive()

                            videoBuffer.poll().getOrThrow()

                            ensureActive()

                            onTimestamp(Timestamp(micros = frame.timestampMicros))

                            ensureActive()

                            renderer.draw(frame).getOrThrow()
                        }
                    }

                    is Frame.Video.EndOfStream -> break
                }
            }
        }

        joinAll(audioJob, videoJob)
    }

    private suspend fun handleAudioPlayback(
        buffer: Buffer<Frame.Audio>,
        sampler: Sampler,
        onTimestamp: suspend (Timestamp) -> Unit,
    ) = with(currentCoroutineContext()) {
        while (isActive) {
            if (bufferLoop.isWaiting.get()) {
                yield()

                continue
            }

            when (val frame = buffer.poll().getOrThrow()) {
                is Frame.Audio.Content -> {
                    ensureActive()

                    onTimestamp(Timestamp(micros = frame.timestampMicros))

                    ensureActive()

                    sampler.play(bytes = frame.bytes).getOrThrow()
                }

                is Frame.Audio.EndOfStream -> break
            }
        }
    }

    private suspend fun handleVideoPlayback(
        buffer: Buffer<Frame.Video>,
        renderer: Renderer,
        onTimestamp: suspend (Timestamp) -> Unit,
    ) = with(currentCoroutineContext()) {
        val startTime = System.nanoTime().nanoseconds

        val playbackSpeedFactor = renderer.playbackSpeedFactor.toDouble()

        var timeShift = Duration.INFINITE

        var elapsedTime: Duration

        while (isActive) {
            if (bufferLoop.isWaiting.get()) {
                yield()

                continue
            }

            val currentTime = System.nanoTime().nanoseconds

            elapsedTime = (currentTime - startTime) * playbackSpeedFactor

            when (val frame = buffer.peek().getOrThrow()) {
                null -> {
                    yield()

                    continue
                }

                is Frame.Video.Content -> {
                    if (timeShift.isInfinite()) {
                        timeShift = frame.timestampMicros.microseconds
                    }
                    if (frame.timestampMicros.microseconds <= elapsedTime + timeShift) {
                        ensureActive()

                        onTimestamp(Timestamp(micros = frame.timestampMicros))

                        ensureActive()

                        renderer.draw(frame).getOrThrow()

                        ensureActive()

                        buffer.poll().getOrThrow()
                    }
                }

                is Frame.Video.EndOfStream -> break
            }
        }
    }

    override suspend fun start(
        onTimestamp: suspend (Timestamp) -> Unit,
        endOfMedia: suspend () -> Unit,
    ) = mutex.withLock {
        runCatching {
            check(!isPlaying.get()) { "Unable to start playback loop, call stop first." }

            job = coroutineScope.launch(context = CoroutineExceptionHandler { _, throwable ->
                if (throwable !is CancellationException) throw throwable
            }) {
                isPlaying.set(true)

                with(pipeline) {
                    when (this) {
                        is Pipeline.Media -> handleMediaPlayback(
                            audioBuffer,
                            videoBuffer,
                            sampler,
                            renderer,
                            onTimestamp
                        )

                        is Pipeline.Audio -> handleAudioPlayback(buffer, sampler, onTimestamp)

                        is Pipeline.Video -> handleVideoPlayback(buffer, renderer, onTimestamp)
                    }
                }

                isPlaying.set(false)

                ensureActive()

                endOfMedia()
            }
        }
    }.onFailure {
        isPlaying.set(false)
    }

    override suspend fun stop() = mutex.withLock {
        val result = runCatching {
            job?.cancelAndJoin()
            job = null
        }

        isPlaying.set(false)

        result
    }

    override fun close() = runCatching { coroutineScope.cancel() }.getOrDefault(Unit)
}