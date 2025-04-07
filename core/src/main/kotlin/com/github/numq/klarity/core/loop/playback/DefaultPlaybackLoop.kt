package com.github.numq.klarity.core.loop.playback

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.Sampler
import com.github.numq.klarity.core.timestamp.Timestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    private val coroutineContext = Dispatchers.Default + Job()

    private val coroutineScope = CoroutineScope(coroutineContext)

    private var job: Job? = null

    private val isPlaying = AtomicBoolean(false)

    private suspend fun handleMediaPlayback(
        media: Media,
        audioBuffer: Buffer<Frame.Audio>,
        videoBuffer: Buffer<Frame.Video>,
        sampler: Sampler,
        renderer: Renderer,
        onAudioTimestamp: suspend (Timestamp) -> Unit,
        onVideoTimestamp: suspend (Timestamp) -> Unit,
    ) {
        val latency = sampler.getLatency().getOrThrow()

        val lastAudioTimestamp = AtomicReference(Duration.INFINITE)

        val audioJob = coroutineScope.launch {
            while (isActive && isPlaying.get()) {
                if (bufferLoop.isWaiting.get()) {
                    yield()

                    continue
                }

                when (val frame = audioBuffer.poll().getOrThrow()) {
                    is Frame.Audio.Content -> {
                        onAudioTimestamp(Timestamp(micros = frame.timestampMicros - latency))

                        lastAudioTimestamp.set((frame.timestampMicros - latency).microseconds)

                        ensureActive()

                        sampler.play(bytes = frame.bytes).getOrThrow()
                    }

                    is Frame.Audio.EndOfStream -> break
                }
            }
        }

        val videoJob = coroutineScope.launch {
            var lastFrameTimestamp = Duration.INFINITE

            while (isActive && isPlaying.get()) {
                if (bufferLoop.isWaiting.get()) {
                    yield()

                    continue
                }

                when {
                    audioJob.isCompleted -> {
                        handleVideoPlayback(
                            media = media, buffer = videoBuffer, renderer = renderer, onTimestamp = onVideoTimestamp
                        )

                        break
                    }

                    else -> when (val frame = videoBuffer.poll().getOrThrow()) {
                        is Frame.Video.Content -> if (lastAudioTimestamp.get().isFinite()) {
                            val startPlaybackTime = System.nanoTime().nanoseconds

                            while (isActive && isPlaying.get()) {
                                if ((System.nanoTime().nanoseconds - startPlaybackTime) * renderer.playbackSpeedFactor.value.toDouble() > frame.timestampMicros.microseconds - lastAudioTimestamp.get()) {
                                    break
                                }

                                ensureActive()

                                yield()
                            }

                            renderer.draw(frame).getOrThrow()

                            ensureActive()

                            onVideoTimestamp(Timestamp(micros = frame.timestampMicros))

                            lastFrameTimestamp = frame.timestampMicros.microseconds
                        }

                        is Frame.Video.EndOfStream -> {
                            if (lastFrameTimestamp.isFinite()) {
                                delay((media.durationMicros.microseconds - lastFrameTimestamp) / renderer.playbackSpeedFactor.value.toDouble())
                            }

                            break
                        }
                    }
                }
            }
        }

        joinAll(audioJob, videoJob)
    }

    private suspend fun handleAudioPlayback(
        buffer: Buffer<Frame.Audio>,
        sampler: Sampler,
        onTimestamp: suspend (Timestamp) -> Unit,
    ) {
        while (currentCoroutineContext().isActive && isPlaying.get()) {
            when (val frame = buffer.poll().getOrThrow()) {
                is Frame.Audio.Content -> {
                    sampler.play(bytes = frame.bytes).getOrThrow()

                    currentCoroutineContext().ensureActive()

                    onTimestamp(Timestamp(micros = frame.timestampMicros))
                }

                is Frame.Audio.EndOfStream -> break
            }
        }
    }

    private suspend fun handleVideoPlayback(
        media: Media,
        buffer: Buffer<Frame.Video>,
        renderer: Renderer,
        onTimestamp: suspend (Timestamp) -> Unit,
    ) {
        var lastFrameTimestamp = Duration.INFINITE

        while (currentCoroutineContext().isActive && isPlaying.get()) {
            when (val frame = buffer.poll().getOrThrow()) {
                is Frame.Video.Content -> {
                    if (lastFrameTimestamp.isInfinite()) {
                        lastFrameTimestamp = frame.timestampMicros.microseconds
                    }

                    val startPlaybackTime = System.nanoTime().nanoseconds

                    while (currentCoroutineContext().isActive && isPlaying.get()) {
                        if ((System.nanoTime().nanoseconds - startPlaybackTime) * renderer.playbackSpeedFactor.value.toDouble() > frame.timestampMicros.microseconds - lastFrameTimestamp) {
                            break
                        }

                        currentCoroutineContext().ensureActive()

                        yield()
                    }

                    renderer.draw(frame).getOrThrow()

                    currentCoroutineContext().ensureActive()

                    onTimestamp(Timestamp(micros = frame.timestampMicros))

                    lastFrameTimestamp = frame.timestampMicros.microseconds
                }

                is Frame.Video.EndOfStream -> {
                    if (lastFrameTimestamp.isFinite()) {
                        delay((media.durationMicros.microseconds - lastFrameTimestamp) / renderer.playbackSpeedFactor.value.toDouble())
                    }

                    break
                }
            }
        }
    }

    override suspend fun start(
        onTimestamp: suspend (Timestamp) -> Unit,
        endOfMedia: suspend () -> Unit,
    ) = mutex.withLock {
        runCatching {
            check(!isPlaying.get()) { "Unable to start playback loop, call stop first." }

            require(job == null) { "Unable to start playback loop" }

            isPlaying.set(true)

            job = coroutineScope.launch {
                try {
                    with(pipeline) {
                        when (this) {
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

                                handleMediaPlayback(
                                    media = media,
                                    audioBuffer = audioBuffer,
                                    videoBuffer = videoBuffer,
                                    sampler = sampler,
                                    renderer = renderer,
                                    onAudioTimestamp = { timestamp ->
                                        audioTimestamps.emit(timestamp)
                                    },
                                    onVideoTimestamp = { timestamp ->
                                        videoTimestamps.emit(timestamp)
                                    },
                                )

                                timestampJob.cancelAndJoin()
                            }

                            is Pipeline.Audio -> handleAudioPlayback(
                                buffer = buffer, sampler = sampler, onTimestamp = onTimestamp
                            )

                            is Pipeline.Video -> handleVideoPlayback(
                                media = media, buffer = buffer, renderer = renderer, onTimestamp = onTimestamp
                            )
                        }
                    }

                    ensureActive()

                    endOfMedia()
                } catch (t: Throwable) {
                    throw t
                } finally {
                    isPlaying.set(false)
                }
            }
        }
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            job?.cancel()
            job = null

            isPlaying.set(false)
        }
    }

    override suspend fun close() = runCatching {
        stop().getOrThrow()

        coroutineScope.cancel()
    }
}