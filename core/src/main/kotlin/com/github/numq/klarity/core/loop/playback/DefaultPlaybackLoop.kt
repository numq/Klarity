package com.github.numq.klarity.core.loop.playback

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.Sampler
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal class DefaultPlaybackLoop(
    private val pipeline: Pipeline,
    private val getPlaybackSpeedFactor: () -> Double,
    private val getRenderer: () -> Renderer?
) : PlaybackLoop {
    private val mutex = Mutex()

    private var job: Job? = null

    private var isPlaying = false

    private val audioClock = AtomicReference(Duration.INFINITE)

    private val videoClock = AtomicReference(Duration.INFINITE)

    private suspend fun handleAudioPlayback(
        mediaDuration: Duration,
        buffer: Buffer<Frame>,
        sampler: Sampler,
        onTimestamp: suspend (Duration) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            when (val frame = buffer.take().getOrThrow()) {
                is Frame.Content.Audio -> {
                    currentCoroutineContext().ensureActive()

                    val frameTime = frame.timestamp

                    onTimestamp(frameTime)

                    audioClock.set(frameTime)

                    sampler.play(frame).getOrThrow()
                }

                is Frame.EndOfStream -> {
                    currentCoroutineContext().ensureActive()

                    val audioClockTime = audioClock.get()

                    if (audioClockTime.isFinite()) {
                        delay((mediaDuration - audioClockTime) / getPlaybackSpeedFactor())
                    }

                    audioClock.set(Duration.INFINITE)

                    break
                }

                else -> error("Unsupported frame type: ${frame::class}")
            }
        }
    }

    private suspend fun handleVideoPlayback(
        mediaDuration: Duration,
        buffer: Buffer<Frame>,
        onTimestamp: suspend (Duration) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            when (val frame = buffer.peek().getOrThrow()) {
                null -> {
                    delay(500.microseconds)

                    continue
                }

                is Frame.Content.Video -> {
                    currentCoroutineContext().ensureActive()

                    val audioClockTime = audioClock.get()

                    val videoClockTime = videoClock.get()

                    val masterClockTime = when {
                        audioClockTime.isFinite() -> audioClockTime

                        videoClockTime.isFinite() -> videoClockTime

                        else -> Duration.INFINITE
                    }

                    val frameTime = frame.timestamp

                    val playbackSpeedFactor = getPlaybackSpeedFactor()

                    if (masterClockTime.isFinite()) {
                        val deltaTime = (frameTime - masterClockTime) / playbackSpeedFactor

                        delay(deltaTime)
                    }

                    currentCoroutineContext().ensureActive()

                    getRenderer()?.render(frame)

                    buffer.take().getOrThrow()

                    onTimestamp(frameTime)

                    videoClock.set(frameTime)
                }

                is Frame.EndOfStream -> {
                    currentCoroutineContext().ensureActive()

                    val videoClockTime = videoClock.get()

                    if (videoClockTime.isFinite()) {
                        delay((mediaDuration - videoClockTime) / getPlaybackSpeedFactor())
                    }

                    videoClock.set(Duration.INFINITE)

                    break
                }

                else -> error("Unsupported frame type: ${frame::class}")
            }
        }
    }

    override suspend fun start(
        coroutineScope: CoroutineScope,
        onException: suspend (PlaybackLoopException) -> Unit,
        onTimestamp: suspend (Duration) -> Unit,
        onEndOfMedia: suspend () -> Unit,
    ) = mutex.withLock {
        runCatching {
            check(!isPlaying) { "Unable to start playback loop, call stop first." }

            require(job == null) { "Unable to start playback loop" }

            isPlaying = true

            val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
                coroutineScope.launch {
                    onException(PlaybackLoopException(exception))
                }
            }

            job = coroutineScope.launch(context = coroutineExceptionHandler) {
                try {
                    with(pipeline) {
                        when (this) {
                            is Pipeline.Audio -> handleAudioPlayback(
                                mediaDuration = media.duration,
                                buffer = buffer,
                                sampler = sampler,
                                onTimestamp = onTimestamp
                            )

                            is Pipeline.Video -> handleVideoPlayback(
                                mediaDuration = media.duration,
                                buffer = buffer,
                                onTimestamp = onTimestamp
                            )

                            is Pipeline.AudioVideo -> {
                                var lastFrameTimestamp = Duration.ZERO

                                val audioJob = launch {
                                    handleAudioPlayback(
                                        mediaDuration = media.duration,
                                        buffer = audioBuffer,
                                        sampler = sampler,
                                        onTimestamp = { frameTimestamp ->
                                            if (frameTimestamp > lastFrameTimestamp) {
                                                onTimestamp(frameTimestamp)

                                                lastFrameTimestamp = frameTimestamp
                                            }
                                        })
                                }

                                val videoJob = launch {
                                    handleVideoPlayback(
                                        mediaDuration = media.duration,
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
                    }

                    ensureActive()

                    onEndOfMedia()
                } catch (t: Throwable) {
                    throw t
                } finally {
                    isPlaying = false

                    audioClock.set(Duration.INFINITE)

                    videoClock.set(Duration.INFINITE)
                }
            }
        }
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            try {
                job?.cancelAndJoin()

                job = null
            } catch (t: Throwable) {
                throw t
            } finally {
                isPlaying = false

                audioClock.set(Duration.INFINITE)

                videoClock.set(Duration.INFINITE)
            }
        }
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            try {
                job?.cancel()

                job = null
            } catch (t: Throwable) {
                throw t
            } finally {
                isPlaying = false

                audioClock.set(Duration.INFINITE)

                videoClock.set(Duration.INFINITE)
            }
        }
    }
}