package loop.playback

import buffer.Buffer
import frame.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import loop.buffer.BufferLoop
import pipeline.Pipeline
import renderer.Renderer
import sampler.Sampler
import timestamp.Timestamp
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

internal class DefaultPlaybackLoop(
    private val bufferLoop: BufferLoop,
    private val pipeline: Pipeline,
) : PlaybackLoop {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var job: Job? = null

    private val isPlaying = AtomicBoolean(false)

    private val _timestamp = MutableStateFlow(Timestamp.ZERO)

    private suspend fun setTimestamp(timestamp: Timestamp) {
        if (isPlaying.get() && timestamp.micros in (0L..pipeline.media.durationMicros)) {
            _timestamp.emit(timestamp)
        }
    }

    private suspend fun handleMediaPlayback(
        audioBuffer: Buffer<Frame.Audio>,
        videoBuffer: Buffer<Frame.Video>,
        sampler: Sampler,
        renderer: Renderer,
    ) {
        val mutex = Mutex()

        var lastAudioTimestamp = Duration.INFINITE

        val audioJob = CoroutineScope(currentCoroutineContext()).launch {
            while (isActive && isPlaying.get()) {
                if (bufferLoop.isWaiting.get()) {
                    yield()

                    continue
                }

                when (val frame = audioBuffer.poll().getOrThrow()) {
                    is Frame.Audio.Content -> {
                        mutex.withLock {
                            lastAudioTimestamp = frame.timestampMicros.microseconds
                        }

                        setTimestamp(Timestamp(micros = frame.timestampMicros))

                        sampler.play(bytes = frame.bytes).getOrThrow()
                    }

                    is Frame.Audio.EndOfStream -> break
                }
            }
        }

        val videoJob = CoroutineScope(currentCoroutineContext()).launch {
            while (isActive && isPlaying.get()) {
                if (bufferLoop.isWaiting.get()) {
                    yield()

                    continue
                }

                when (val frame = videoBuffer.peek().getOrThrow()) {
                    null -> {
                        yield()

                        continue
                    }

                    is Frame.Video.Content -> if (lastAudioTimestamp.isFinite()) {
                        if (frame.timestampMicros.microseconds <= mutex.withLock { lastAudioTimestamp }) {
                            videoBuffer.poll().getOrThrow()

                            setTimestamp(Timestamp(micros = frame.timestampMicros))

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
    ) {
        while (currentCoroutineContext().isActive && isPlaying.get()) {
            if (bufferLoop.isWaiting.get()) {
                yield()

                continue
            }

            when (val frame = buffer.poll().getOrThrow()) {
                is Frame.Audio.Content -> {
                    setTimestamp(Timestamp(micros = frame.timestampMicros))

                    sampler.play(bytes = frame.bytes).getOrThrow()
                }

                is Frame.Audio.EndOfStream -> break
            }
        }
    }

    private suspend fun handleVideoPlayback(
        buffer: Buffer<Frame.Video>,
        renderer: Renderer,
    ) {
        val startTime = System.nanoTime().nanoseconds

        val playbackSpeedFactor = renderer.playbackSpeedFactor.toDouble()

        var timeShift = Duration.INFINITE

        var elapsedTime: Duration

        while (currentCoroutineContext().isActive && isPlaying.get()) {
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
                        setTimestamp(Timestamp(micros = frame.timestampMicros))

                        renderer.draw(frame).getOrThrow()

                        buffer.poll().getOrThrow()
                    }
                }

                is Frame.Video.EndOfStream -> break
            }
        }
    }

    override val timestamp = _timestamp.asStateFlow()

    override suspend fun start(endOfMedia: suspend () -> Unit) = runCatching {
        check(!isPlaying.get()) { "Unable to start playback loop, call stop first." }

        job = coroutineScope.launch(context = CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) throw throwable
        }) {
            isPlaying.set(true)

            with(pipeline) {
                when (this) {
                    is Pipeline.Media -> handleMediaPlayback(audioBuffer, videoBuffer, sampler, renderer)

                    is Pipeline.Audio -> handleAudioPlayback(buffer, sampler)

                    is Pipeline.Video -> handleVideoPlayback(buffer, renderer)
                }
            }

            isPlaying.set(false)

            endOfMedia()
        }
    }.onFailure {
        isPlaying.set(false)
    }

    override suspend fun stop(resetTime: Boolean) = runCatching {
        job?.cancelAndJoin()
        job = null

        if (resetTime) {
            _timestamp.emit(Timestamp.ZERO)
        }

        isPlaying.set(false)
    }.onFailure {
        isPlaying.set(false)
    }

    override suspend fun seekTo(timestamp: Timestamp) = runCatching {
        val targetTimestamp: Timestamp

        while (currentCoroutineContext().isActive) {
            targetTimestamp = when (pipeline) {
                is Pipeline.Media -> when (
                    val frame = pipeline.audioBuffer.peek().getOrThrow()
                        ?: pipeline.videoBuffer.peek().getOrThrow()
                        ?: continue
                ) {
                    is Frame.Audio.Content -> Timestamp(micros = frame.timestampMicros)

                    is Frame.Video.Content -> {
                        pipeline.renderer.draw(frame)

                        Timestamp(micros = frame.timestampMicros)
                    }

                    is Frame.Audio.EndOfStream,
                    is Frame.Video.EndOfStream,
                    -> Timestamp(micros = pipeline.media.durationMicros)
                }

                is Pipeline.Audio -> when (val frame = pipeline.buffer.peek().getOrThrow() ?: continue) {
                    is Frame.Audio.Content -> Timestamp(micros = frame.timestampMicros)

                    is Frame.Audio.EndOfStream -> Timestamp(micros = pipeline.media.durationMicros)
                }

                is Pipeline.Video -> when (val frame = pipeline.buffer.peek().getOrThrow() ?: continue) {
                    is Frame.Video.Content -> {
                        pipeline.renderer.draw(frame)

                        Timestamp(micros = frame.timestampMicros)
                    }

                    is Frame.Video.EndOfStream -> Timestamp(micros = pipeline.media.durationMicros)
                }
            }

            _timestamp.emit(targetTimestamp)

            _timestamp.first()

            break
        }
    }

    override fun close() = runCatching {
        coroutineScope.cancel()
    }.getOrDefault(Unit)
}