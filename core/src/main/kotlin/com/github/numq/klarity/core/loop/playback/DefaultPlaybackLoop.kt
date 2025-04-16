package com.github.numq.klarity.core.loop.playback

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.Sampler
import com.github.numq.klarity.core.timestamp.Timestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.nanoseconds

internal class DefaultPlaybackLoop(
    private val pipeline: Pipeline,
    private val getPlaybackSpeedFactor: () -> Double,
    private val getRenderer: () -> Renderer?
) : PlaybackLoop {
    private val mutex = Mutex()

    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private var job: Job? = null

    private val isPlaying = AtomicBoolean(false)

    private suspend fun handleMediaPlayback(
        media: Media,
        audioBuffer: Buffer<Frame.Audio>,
        videoBuffer: Buffer<Frame.Video>,
        sampler: Sampler,
        onAudioTimestamp: suspend (Timestamp) -> Unit,
        onVideoTimestamp: suspend (Timestamp) -> Unit,
    ) {
        val latency = sampler.getLatency().getOrThrow()

        var lastAudioTimestamp = Duration.INFINITE

        val audioJob = coroutineScope.launch {
            while (currentCoroutineContext().isActive && isPlaying.get()) {
                when (val frame = audioBuffer.poll().getOrThrow()) {
                    is Frame.Audio.Content -> {
                        onAudioTimestamp(Timestamp(micros = frame.timestampMicros - latency))

                        lastAudioTimestamp = (frame.timestampMicros - latency).microseconds

                        sampler.play(bytes = frame.bytes).getOrThrow()
                    }

                    is Frame.Audio.EndOfStream -> break
                }
            }
        }

        val videoJob = coroutineScope.launch {
            var lastFrameTimestamp = Duration.INFINITE

            while (currentCoroutineContext().isActive && isPlaying.get()) {
                when {
                    audioJob.isCompleted -> {
                        handleVideoPlayback(
                            media = media,
                            buffer = videoBuffer,
                            onTimestamp = onVideoTimestamp
                        )

                        break
                    }

                    else -> when (val frame = videoBuffer.poll().getOrThrow()) {
                        is Frame.Video.Content -> if (lastAudioTimestamp.isFinite()) {
                            val startPlaybackTime = System.nanoTime().nanoseconds

                            while (currentCoroutineContext().isActive && isPlaying.get()) {
                                if ((System.nanoTime().nanoseconds - startPlaybackTime) * getPlaybackSpeedFactor() > frame.timestampMicros.microseconds - lastAudioTimestamp) {
                                    break
                                }

                                delay(10L)
                            }

                            currentCoroutineContext().ensureActive()

                            getRenderer()?.render(frame)

                            onVideoTimestamp(Timestamp(micros = frame.timestampMicros))

                            lastFrameTimestamp = frame.timestampMicros.microseconds
                        }

                        is Frame.Video.EndOfStream -> {
                            if (lastFrameTimestamp.isFinite()) {
                                delay((media.durationMicros.microseconds - lastFrameTimestamp) / getPlaybackSpeedFactor())
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

                    onTimestamp(Timestamp(micros = frame.timestampMicros))
                }

                is Frame.Audio.EndOfStream -> break
            }
        }
    }

    private suspend fun handleVideoPlayback(
        media: Media,
        buffer: Buffer<Frame.Video>,
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
                        if ((System.nanoTime().nanoseconds - startPlaybackTime) * getPlaybackSpeedFactor() > frame.timestampMicros.microseconds - lastFrameTimestamp) {
                            break
                        }

                        delay(10L)
                    }

                    currentCoroutineContext().ensureActive()

                    getRenderer()?.render(frame)

                    onTimestamp(Timestamp(micros = frame.timestampMicros))

                    lastFrameTimestamp = frame.timestampMicros.microseconds
                }

                is Frame.Video.EndOfStream -> {
                    if (lastFrameTimestamp.isFinite()) {
                        delay((media.durationMicros.microseconds - lastFrameTimestamp) / getPlaybackSpeedFactor())
                    }

                    break
                }
            }
        }
    }

    override suspend fun start(
        onException: suspend (PlaybackLoopException) -> Unit,
        onTimestamp: suspend (Timestamp) -> Unit,
        endOfMedia: suspend () -> Unit,
    ) = mutex.withLock {
        runCatching {
            check(!isPlaying.get()) { "Unable to start playback loop, call stop first." }

            require(job == null) { "Unable to start playback loop" }

            isPlaying.set(true)

            val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
                coroutineScope.launch {
                    onException(PlaybackLoopException(exception))
                }
            }

            job = coroutineScope.launch(context = coroutineExceptionHandler) {
                try {
                    with(pipeline) {
                        when (this) {
                            is Pipeline.AudioVideo -> handleMediaPlayback(
                                media = media,
                                audioBuffer = audioBuffer,
                                videoBuffer = videoBuffer,
                                sampler = sampler,
                                onAudioTimestamp = onTimestamp,
                                onVideoTimestamp = onTimestamp,
                            )

                            is Pipeline.Audio -> handleAudioPlayback(
                                buffer = buffer, sampler = sampler, onTimestamp = onTimestamp
                            )

                            is Pipeline.Video -> handleVideoPlayback(
                                media = media, buffer = buffer, onTimestamp = onTimestamp
                            )
                        }
                    }

                    currentCoroutineContext().ensureActive()

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