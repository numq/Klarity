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
            val frame = buffer.poll().getOrThrow()

            try {
                when (frame) {
                    is Frame.Content.Audio -> {
                        currentCoroutineContext().ensureActive()

                        val frameTime = frame.timestamp

                        onTimestamp(frameTime)

                        audioClock.set(frameTime)

                        sampler.play(frame).getOrThrow()
                    }

                    is Frame.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        withContext(Dispatchers.IO) { frame.close() }

                        val audioClockTime = audioClock.get()

                        if (audioClockTime.isFinite()) {
                            delay((mediaDuration - audioClockTime) / getPlaybackSpeedFactor())
                        }

                        audioClock.set(Duration.INFINITE)

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

    /*private suspend fun handleVideoPlayback(
        frameRate: Double,
        mediaDuration: Duration,
        buffer: Buffer<Frame>,
        onTimestamp: suspend (Duration) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            val frame = buffer.poll().getOrThrow()

            try {
                when (frame) {
                    is Frame.Content.Video -> {
                        currentCoroutineContext().ensureActive()

                        var renderingRequested = false

                        val frameTime = frame.timestamp

                        val audioClockTime = audioClock.get()

                        val videoClockTime = videoClock.get()

                        val initialTime = System.nanoTime().nanoseconds

                        while (currentCoroutineContext().isActive) {
                            val playbackSpeedFactor = getPlaybackSpeedFactor()

                            val masterClockTime = when {
                                audioClockTime.isFinite() -> audioClock.get()

                                videoClockTime.isFinite() -> videoClock.get()

                                else -> Duration.ZERO
                            }

                            val deltaTime = (frameTime - masterClockTime) / playbackSpeedFactor

                            println(deltaTime)

                            val renderTime = 1.seconds / frameRate / playbackSpeedFactor * 2

                            println(renderTime)

                            if (deltaTime < -renderTime) {
                                break
                            }

                            if ((System.nanoTime().nanoseconds - initialTime) / playbackSpeedFactor > deltaTime) {
                                renderingRequested = true

                                break
                            }

                            delay(500.microseconds)
                        }

                        if (!renderingRequested) {
                            println("skipping frame")

                            withContext(Dispatchers.IO) { frame.close() }

                            continue
                        }

                        currentCoroutineContext().ensureActive()

                        onTimestamp(frameTime)

                        getRenderer()?.render(frame)

                        videoClock.set(frameTime)
                    }

                    is Frame.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        withContext(Dispatchers.IO) { frame.close() }

                        val videoClockTime = videoClock.get()

                        if (videoClockTime.isFinite()) {
                            delay((mediaDuration - videoClockTime) / getPlaybackSpeedFactor())
                        }

                        videoClock.set(Duration.INFINITE)

                        break
                    }

                    else -> error("Unsupported frame type: ${frame::class}")
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.IO) { frame.close() }
                throw t
            }
        }
    }*/

    private suspend fun handleVideoPlayback(
        mediaDuration: Duration,
        buffer: Buffer<Frame>,
        onTimestamp: suspend (Duration) -> Unit,
    ) {
        while (currentCoroutineContext().isActive) {
            val frame = buffer.poll().getOrThrow()

            try {
                when (frame) {
                    is Frame.Content.Video -> {
                        currentCoroutineContext().ensureActive()

                        val audioClockTime = audioClock.get()

                        val videoClockTime = videoClock.get()

                        val masterClockTime = when {
                            audioClockTime.isFinite() -> audioClockTime

                            videoClockTime.isFinite() -> videoClockTime

                            else -> Duration.ZERO
                        }

                        val frameTime = frame.timestamp

                        val playbackSpeedFactor = getPlaybackSpeedFactor()

                        val deltaTime = (frameTime - masterClockTime) / playbackSpeedFactor

                        delay(deltaTime)

                        currentCoroutineContext().ensureActive()

                        getRenderer()?.render(frame)

                        onTimestamp(frameTime)

                        videoClock.set(frameTime)
                    }

                    is Frame.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        withContext(Dispatchers.IO) { frame.close() }

                        val videoClockTime = videoClock.get()

                        if (videoClockTime.isFinite()) {
                            delay((mediaDuration - videoClockTime) / getPlaybackSpeedFactor())
                        }

                        videoClock.set(Duration.INFINITE)

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
                                mediaDuration = media.duration, buffer = buffer, onTimestamp = onTimestamp
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

                    audioClock.set(Duration.INFINITE)

                    videoClock.set(Duration.INFINITE)
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
            } finally {
                isPlaying = false

                audioClock.set(Duration.INFINITE)

                videoClock.set(Duration.INFINITE)
            }
        }
    }
}