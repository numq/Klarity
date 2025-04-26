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
import kotlin.time.Duration.Companion.nanoseconds

internal class DefaultPlaybackLoop(
    private val pipeline: Pipeline,
    private val getPlaybackSpeedFactor: () -> Double,
    private val getRenderer: () -> Renderer?
) : PlaybackLoop {
    private val mutex = Mutex()

    private var job: Job? = null

    private var isPlaying = false

    private val audioClockTime = AtomicReference(Duration.INFINITE)

    private val videoClockTime = AtomicReference(Duration.INFINITE)

    private suspend fun handleAudioPlayback(
        mediaDuration: Duration,
        buffer: Buffer<Frame>,
        sampler: Sampler,
        onTimestamp: suspend (Duration) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            val frame = buffer.poll().getOrThrow()

            try {
                when (frame) {
                    is Frame.Content.Audio -> {
                        currentCoroutineContext().ensureActive()

                        sampler.play(frame).getOrThrow()

                        val frameTime = frame.timestamp

                        onTimestamp(frameTime)

                        audioClockTime.set(frameTime)
                    }

                    is Frame.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        withContext(Dispatchers.IO) { frame.close() }

                        if (audioClockTime.get().isFinite()) {
                            delay((mediaDuration - audioClockTime.get()) / getPlaybackSpeedFactor())
                        }

                        audioClockTime.set(Duration.INFINITE)

                        break
                    }

                    else -> error("Unsupported frame type: ${frame::class}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.IO) { frame.close() }

                throw t
            }

            delay(500.microseconds)
        }
    }

    private suspend fun handleVideoPlayback(
        mediaDuration: Duration,
        buffer: Buffer<Frame>,
        onTimestamp: suspend (Duration) -> Unit,
    ) {
        val playbackStartTime = System.nanoTime().nanoseconds

        while (currentCoroutineContext().isActive) {
            val frame = buffer.poll().getOrThrow()

            try {
                when (frame) {
                    is Frame.Content.Video -> {
                        currentCoroutineContext().ensureActive()

                        val frameTime = frame.timestamp

                        while (currentCoroutineContext().isActive) {
                            val elapsedTime = System.nanoTime().nanoseconds - playbackStartTime

                            val clockTime = audioClockTime.get().takeIf(Duration::isFinite)
                                ?: (videoClockTime.get() + elapsedTime * getPlaybackSpeedFactor())

                            val diffTime = frame.timestamp - clockTime

                            if (diffTime <= Duration.ZERO) break

                            delay(500.microseconds)
                        }

                        currentCoroutineContext().ensureActive()

                        getRenderer()?.render(frame)

                        onTimestamp(frame.timestamp)

                        videoClockTime.set(frameTime)
                    }

                    is Frame.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        withContext(Dispatchers.IO) { frame.close() }

                        if (videoClockTime.get().isFinite()) {
                            delay((mediaDuration - videoClockTime.get()) / getPlaybackSpeedFactor())
                        }

                        videoClockTime.set(Duration.INFINITE)

                        break
                    }

                    else -> error("Unsupported frame type: ${frame::class}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.IO) { frame.close() }
                throw t
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

                            is Pipeline.AudioVideoSingleDecoder -> {
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
                } finally {
                    isPlaying = false

                    audioClockTime.set(Duration.INFINITE)

                    videoClockTime.set(Duration.INFINITE)
                }
            }
        }
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            try {
                job?.cancel()

                job = null
            } finally {
                isPlaying = false

                audioClockTime.set(Duration.INFINITE)

                videoClockTime.set(Duration.INFINITE)
            }
        }
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            try {
                job?.cancel()

                job = null
            } finally {
                isPlaying = false

                audioClockTime.set(Duration.INFINITE)

                videoClockTime.set(Duration.INFINITE)
            }
        }
    }
}