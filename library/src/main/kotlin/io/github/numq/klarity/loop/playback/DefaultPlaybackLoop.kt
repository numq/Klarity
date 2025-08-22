package io.github.numq.klarity.loop.playback

import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pipeline.Pipeline
import io.github.numq.klarity.renderer.Renderer
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal class DefaultPlaybackLoop(
    private val media: Media,
    private val pipeline: Pipeline,
    private val syncThreshold: Duration,
    private val getVolume: () -> Float,
    private val getPlaybackSpeedFactor: () -> Float,
    private val getRenderer: () -> Renderer?
) : PlaybackLoop {
    private val mutex = Mutex()

    private var job: Job? = null

    private val audioClock = AtomicReference(Duration.INFINITE)

    private val videoClock = AtomicReference(Duration.INFINITE)

    private suspend fun Pipeline.AudioPipeline.handleAudioPlayback(onTimestamp: suspend (Duration) -> Unit) {
        val latency = sampler.getLatency().getOrThrow().microseconds

        while (currentCoroutineContext().isActive) {
            val frame = buffer.take().getOrThrow()

            currentCoroutineContext().ensureActive()

            when (frame) {
                is Frame.Content.Audio -> {
                    val frameTime = frame.timestamp - latency

                    audioClock.set(frameTime)

                    onTimestamp(frameTime)

                    sampler.write(
                        frame = frame, volume = getVolume(), playbackSpeedFactor = getPlaybackSpeedFactor()
                    ).getOrThrow()
                }

                is Frame.EndOfStream -> {
                    sampler.drain(volume = getVolume(), playbackSpeedFactor = getPlaybackSpeedFactor()).getOrThrow()

                    audioClock.set(Duration.INFINITE)

                    break
                }

                else -> error("Unsupported frame type: ${frame::class}")
            }
        }
    }

    private suspend fun Pipeline.VideoPipeline.handleVideoPlayback(
        getRenderer: () -> Renderer?, onTimestamp: suspend (Duration) -> Unit
    ) {
        while (currentCoroutineContext().isActive) {
            val frame = buffer.take().getOrThrow()

            try {
                currentCoroutineContext().ensureActive()

                when (frame) {
                    is Frame.Content.Video -> {
                        val audioClockTime = audioClock.get()

                        val videoClockTime = videoClock.get()

                        val frameTime = frame.timestamp

                        val playbackSpeedFactor = getPlaybackSpeedFactor()

                        when {
                            audioClockTime.isFinite() -> {
                                val deltaTime = frameTime - audioClockTime

                                when {
                                    deltaTime < -syncThreshold -> continue

                                    deltaTime > syncThreshold -> delay((deltaTime / playbackSpeedFactor.toDouble()))
                                }
                            }

                            videoClockTime.isFinite() -> {
                                val deltaTime = frameTime - videoClockTime

                                delay((deltaTime / playbackSpeedFactor.toDouble()))
                            }
                        }

                        currentCoroutineContext().ensureActive()

                        videoClock.set(frameTime)

                        onTimestamp(frameTime)

                        getRenderer()?.render(frame)?.getOrThrow()
                    }

                    is Frame.EndOfStream -> {
                        val videoClockTime = videoClock.get()

                        if (videoClockTime.isFinite()) {
                            delay((media.duration - videoClockTime) / getPlaybackSpeedFactor().toDouble())
                        }

                        videoClock.set(Duration.INFINITE)

                        break
                    }

                    else -> error("Unsupported frame type: ${frame::class}")
                }
            } finally {
                if (frame is Frame.Content.Video) {
                    pool.release(item = frame.data).getOrThrow()
                }
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
            check(job == null) { "Unable to start playback loop, call stop first." }

            val coroutineExceptionHandler = CoroutineExceptionHandler { _, exception ->
                coroutineScope.launch {
                    onException(PlaybackLoopException(exception))
                }
            }

            job = coroutineScope.launch(context = coroutineExceptionHandler) {
                with(pipeline) {
                    var lastFrameTimestamp = Duration.ZERO

                    val audioJob = launch {
                        audioPipeline?.handleAudioPlayback(onTimestamp = { frameTimestamp ->
                            if (frameTimestamp > lastFrameTimestamp) {
                                onTimestamp(frameTimestamp)

                                lastFrameTimestamp = frameTimestamp
                            }
                        })
                    }

                    val videoJob = launch {
                        videoPipeline?.handleVideoPlayback(getRenderer = getRenderer, onTimestamp = { frameTimestamp ->
                            if (frameTimestamp > lastFrameTimestamp) {
                                onTimestamp(frameTimestamp)

                                lastFrameTimestamp = frameTimestamp
                            }
                        })
                    }

                    joinAll(audioJob, videoJob)

                    ensureActive()

                    onTimestamp(media.duration)

                    onEndOfMedia()
                }
            }
        }
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            try {
                job?.cancelAndJoin()
            } catch (_: CancellationException) {

            } finally {
                job = null

                audioClock.set(Duration.INFINITE)

                videoClock.set(Duration.INFINITE)
            }

            Unit
        }
    }

    override suspend fun close() = mutex.withLock {
        runCatching {
            job?.cancel()

            job = null
        }
    }
}