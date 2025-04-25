package com.github.numq.klarity.core.loop.playback

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.Sampler
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultPlaybackLoop(
    private val pipeline: Pipeline,
    private val getPlaybackSpeedFactor: () -> Double,
    private val getRenderer: () -> Renderer?
) : PlaybackLoop {
    private val mutex = Mutex()

    private var job: Job? = null

    private var isPlaying = false

    private suspend fun handleAudioPlayback(
        media: Media.Audio,
        buffer: Buffer<Frame.Audio>,
        sampler: Sampler,
        onTimestamp: suspend (Duration) -> Unit,
    ) {
        var lastFrameTime = Duration.INFINITE

        while (currentCoroutineContext().isActive) {
            val frame = buffer.poll().getOrThrow()

            try {
                when (frame) {
                    is Frame.Audio.Content -> {
                        currentCoroutineContext().ensureActive()

                        onTimestamp(lastFrameTime)

                        sampler.play(frame).getOrThrow()

                        lastFrameTime = frame.timestamp
                    }

                    is Frame.Audio.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        withContext(Dispatchers.IO) { frame.close() }

                        if (lastFrameTime.isFinite()) {
                            delay((media.duration - lastFrameTime) / getPlaybackSpeedFactor())
                        }

                        break
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.IO) { frame.close() }

                throw t
            }

            delay(500.microseconds)
        }
    }

    private suspend fun handleVideoPlayback(
        media: Media.Video,
        buffer: Buffer<Frame.Video>,
        onTimestamp: suspend (Duration) -> Unit,
    ) {
        var lastFrameTime = Duration.INFINITE

        while (currentCoroutineContext().isActive) {
            val frame = buffer.poll().getOrThrow()

            try {
                when (frame) {
                    is Frame.Video.Content -> {
                        currentCoroutineContext().ensureActive()

                        val frameTime = frame.timestamp

                        if (lastFrameTime.isFinite()) {
                            val diffTime = frameTime - lastFrameTime

                            delay(diffTime / getPlaybackSpeedFactor())
                        }

                        onTimestamp(frame.timestamp)

                        getRenderer()?.render(frame)

                        lastFrameTime = frame.timestamp
                    }

                    is Frame.Video.EndOfStream -> {
                        currentCoroutineContext().ensureActive()

                        withContext(Dispatchers.IO) { frame.close() }

                        if (lastFrameTime.isFinite()) {
                            delay((media.duration - lastFrameTime) / getPlaybackSpeedFactor())
                        }
                    }
                }
            } catch (t: Throwable) {
                withContext(Dispatchers.IO) { frame.close() }

                throw t
            }

            delay(500.microseconds)
        }
    }

    @OptIn(DelicateCoroutinesApi::class)
    private suspend fun handleMediaPlayback(
        media: Media.AudioVideo,
        audioBuffer: Buffer<Frame.Audio>,
        videoBuffer: Buffer<Frame.Video>,
        sampler: Sampler,
        onAudioTimestamp: suspend (Duration) -> Unit,
        onVideoTimestamp: suspend (Duration) -> Unit,
    ) = coroutineScope {
        val latencyTime = sampler.getLatency().getOrThrow().microseconds

        val syncTime = Channel<Duration>(Channel.CONFLATED)

        val audioJob = launch {
            var lastFrameTime = Duration.INFINITE

            while (isActive) {
                val frame = audioBuffer.poll().getOrThrow()

                try {
                    when (frame) {
                        is Frame.Audio.Content -> {
                            ensureActive()

                            val frameTime = frame.timestamp - latencyTime

                            syncTime.trySend(frameTime)

                            onAudioTimestamp(frame.timestamp)

                            sampler.play(frame).getOrThrow()

                            lastFrameTime = frame.timestamp
                        }

                        is Frame.Audio.EndOfStream -> {
                            ensureActive()

                            withContext(Dispatchers.IO) { frame.close() }

                            if (lastFrameTime.isFinite()) {
                                delay((media.duration - lastFrameTime) / this@DefaultPlaybackLoop.getPlaybackSpeedFactor())
                            }

                            syncTime.close()

                            break
                        }
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.IO) { frame.close() }
                }
            }
        }

        val videoJob = launch {
            val thresholdTime = 5.milliseconds

            var lastFrameTime = Duration.INFINITE

            while (isActive) {
                val frame = videoBuffer.poll().getOrThrow()

                try {
                    when (frame) {
                        is Frame.Video.Content -> {
                            ensureActive()

                            val targetTime = if (syncTime.isClosedForReceive) {
                                lastFrameTime.takeIf(Duration::isFinite) ?: Duration.ZERO
                            } else {
                                syncTime.receive()
                            }

                            val frameTime = frame.timestamp

                            val diffTime = frameTime - targetTime

                            if (diffTime > thresholdTime) {
                                delay(diffTime / getPlaybackSpeedFactor())
                            } else if (diffTime < -thresholdTime) {
                                withContext(Dispatchers.IO) { frame.close() }

                                continue
                            }

                            getRenderer()?.render(frame)

                            onVideoTimestamp(frame.timestamp)

                            lastFrameTime = frame.timestamp
                        }

                        is Frame.Video.EndOfStream -> {
                            ensureActive()

                            withContext(Dispatchers.IO) { frame.close() }

                            if (lastFrameTime.isFinite()) {
                                delay((media.duration - lastFrameTime) / this@DefaultPlaybackLoop.getPlaybackSpeedFactor())
                            }

                            break
                        }
                    }
                } catch (t: Throwable) {
                    withContext(Dispatchers.IO) { frame.close() }
                }
            }
        }

        joinAll(audioJob, videoJob)
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
                                media = media,
                                buffer = buffer,
                                sampler = sampler,
                                onTimestamp = onTimestamp
                            )

                            is Pipeline.Video -> handleVideoPlayback(
                                media = media,
                                buffer = buffer,
                                onTimestamp = onTimestamp
                            )

                            is Pipeline.AudioVideo -> {
                                var lastFrameTimestamp = Duration.ZERO

                                handleMediaPlayback(
                                    media = media,
                                    audioBuffer = audioBuffer,
                                    videoBuffer = videoBuffer,
                                    sampler = sampler,
                                    onAudioTimestamp = { frameTimestamp ->
                                        if (frameTimestamp > lastFrameTimestamp) {
                                            onTimestamp(frameTimestamp)

                                            lastFrameTimestamp = frameTimestamp
                                        }
                                    },
                                    onVideoTimestamp = { frameTimestamp ->
                                        if (frameTimestamp > lastFrameTimestamp) {
                                            onTimestamp(frameTimestamp)

                                            lastFrameTimestamp = frameTimestamp
                                        }
                                    })
                            }
                        }
                    }

                    ensureActive()

                    onEndOfMedia()
                } finally {
                    isPlaying = false
                }
            }
        }
    }

    override suspend fun stop() = mutex.withLock {
        runCatching {
            try {
                job?.cancelAndJoin()
                job = null
            } finally {
                isPlaying = false
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
            }
        }
    }
}