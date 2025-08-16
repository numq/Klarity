package io.github.numq.klarity.controller

import io.github.numq.klarity.buffer.BufferFactory
import io.github.numq.klarity.command.Command
import io.github.numq.klarity.controller.PlayerController.Companion.MAX_PLAYBACK_SPEED_FACTOR
import io.github.numq.klarity.controller.PlayerController.Companion.MIN_PLAYBACK_SPEED_FACTOR
import io.github.numq.klarity.decoder.AudioDecoderFactory
import io.github.numq.klarity.decoder.Decoder
import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.event.PlayerEvent
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.loop.buffer.BufferLoop
import io.github.numq.klarity.loop.buffer.BufferLoopFactory
import io.github.numq.klarity.loop.playback.PlaybackLoop
import io.github.numq.klarity.loop.playback.PlaybackLoopFactory
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pipeline.Pipeline
import io.github.numq.klarity.pool.PoolFactory
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.sampler.SamplerFactory
import io.github.numq.klarity.settings.PlayerSettings
import io.github.numq.klarity.state.Destination
import io.github.numq.klarity.state.InternalPlayerState
import io.github.numq.klarity.state.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.jetbrains.skia.Data
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultPlayerController(
    private val initialSettings: PlayerSettings?,
    private val audioDecoderFactory: AudioDecoderFactory,
    private val videoDecoderFactory: VideoDecoderFactory,
    private val poolFactory: PoolFactory,
    private val bufferFactory: BufferFactory,
    private val bufferLoopFactory: BufferLoopFactory,
    private val playbackLoopFactory: PlaybackLoopFactory,
    private val samplerFactory: SamplerFactory,
) : PlayerController {
    private companion object {
        val SYNC_THRESHOLD = 20.milliseconds
    }

    /**
     * Coroutines
     */

    private val supervisorJob = SupervisorJob()

    private val bufferScope = CoroutineScope(Dispatchers.Default + supervisorJob + CoroutineName("BufferScope"))

    private val playbackScope = CoroutineScope(Dispatchers.Default + supervisorJob + CoroutineName("PlaybackScope"))

    private val controllerScope = CoroutineScope(Dispatchers.Default + supervisorJob + CoroutineName("ControllerScope"))

    /**
     * Renderer
     */

    @Volatile
    private var renderer: Renderer? = null

    private suspend fun renderFirstFrame(pipeline: Pipeline) = with(pipeline) {
        when (this) {
            is Pipeline.Audio -> null

            is Pipeline.Video -> pool to decoder

            is Pipeline.AudioVideo -> videoPool to videoDecoder
        }?.let { (pool, decoder) ->
            val data = pool.acquire().getOrThrow()

            try {
                val frame = decoder.decodeVideo(data = data).getOrThrow() as? Frame.Content.Video

                if (frame != null) {
                    renderer?.render(frame)?.getOrThrow()
                }
            } finally {
                pool.release(item = data).getOrThrow()
            }
        }
    }

    /**
     * Settings
     */

    private val defaultSettings = PlayerSettings.DEFAULT

    override val settings = MutableStateFlow(initialSettings ?: defaultSettings)

    /**
     * State
     */

    internal var onInternalPlayerState: ((InternalPlayerState) -> Unit)? = null

    private val internalState = MutableStateFlow<InternalPlayerState>(InternalPlayerState.Empty)

    override val state = MutableStateFlow<PlayerState>(PlayerState.Empty)

    private suspend fun updateState(internalState: InternalPlayerState) {
        onInternalPlayerState?.invoke(internalState)

        val updatedState = when (internalState) {
            is InternalPlayerState.Empty -> PlayerState.Empty

            is InternalPlayerState.Preparing -> PlayerState.Preparing

            is InternalPlayerState.Releasing -> PlayerState.Releasing(previousState = state.value)

            is InternalPlayerState.Error -> PlayerState.Error(
                exception = internalState.cause as? Exception ?: Exception(internalState.cause),
                previousState = state.value
            )

            is InternalPlayerState.Ready -> when (internalState) {
                is InternalPlayerState.Ready.Playing -> PlayerState.Ready.Playing(media = internalState.media)

                is InternalPlayerState.Ready.Paused -> PlayerState.Ready.Paused(media = internalState.media)

                is InternalPlayerState.Ready.Stopped -> PlayerState.Ready.Stopped(media = internalState.media)

                is InternalPlayerState.Ready.Completed -> PlayerState.Ready.Completed(media = internalState.media)

                is InternalPlayerState.Ready.Seeking -> PlayerState.Ready.Seeking(media = internalState.media)

                is InternalPlayerState.Ready.Transition -> null
            }
        }

        if (updatedState != null) {
            state.emit(updatedState)
        }
    }

    private suspend fun updateInternalState(updatedState: InternalPlayerState) {
        internalState.emit(updatedState)

        updateState(updatedState)
    }

    private suspend fun InternalPlayerState.Ready.withTransition(
        destination: Destination,
        block: suspend () -> Unit = {},
    ) {
        val transition = startTransition(destination = destination)

        updateInternalState(transition)

        return try {
            val updatedState = completeTransition(transition)

            block.invoke()

            updateInternalState(updatedState)
        } catch (e: Exception) {
            updateInternalState(this)

            throw e
        }
    }

    /**
     * Timestamp
     */

    override val bufferTimestamp = MutableStateFlow(Duration.ZERO)

    override val playbackTimestamp = MutableStateFlow(Duration.ZERO)

    private suspend fun handleBufferTimestamp(timestamp: Duration) = when (internalState.value) {
        is InternalPlayerState.Ready.Playing,
        is InternalPlayerState.Ready.Paused,
            -> bufferTimestamp.emit(timestamp)

        else -> Unit
    }

    private suspend fun handlePlaybackTimestamp(timestamp: Duration) = when (internalState.value) {
        is InternalPlayerState.Ready.Playing -> playbackTimestamp.emit(timestamp)

        else -> Unit
    }

    /**
     * Events
     */

    private suspend fun handleException(throwable: Throwable) =
        _events.send(PlayerEvent.Error(throwable as? Exception ?: Exception(throwable)))

    /**
     * Completion
     */

    private suspend fun InternalPlayerState.Ready.handleBufferCompletion() {
        bufferLoop.stop().getOrThrow()

        _events.send(PlayerEvent.Buffer.Complete)
    }

    private suspend fun InternalPlayerState.Ready.handlePlaybackCompletion() {
        withTransition(Destination.COMPLETED) {
            playbackLoop.stop().getOrThrow()

            when (val pipeline = pipeline) {
                is Pipeline.Audio -> pipeline.sampler.stop().getOrThrow()

                is Pipeline.Video -> Unit

                is Pipeline.AudioVideo -> pipeline.sampler.stop().getOrThrow()
            }
        }
    }

    /**
     * Command
     */

    private val commandMutex = Mutex()

    private suspend fun handlePrepare(
        location: String,
        audioBufferSize: Int,
        videoBufferSize: Int,
        hardwareAccelerationCandidates: List<HardwareAcceleration>?,
    ): Triple<Pipeline, BufferLoop, PlaybackLoop> {
        val media = Decoder.probe(
            location = location, findAudioStream = audioBufferSize > 0, findVideoStream = videoBufferSize > 0
        ).getOrThrow()

        check(!media.duration.isNegative()) { "Media does not support playback" }

        val pipeline = when (media) {
            is Media.Audio -> with(media) {
                val decoder = audioDecoderFactory.create(
                    parameters = AudioDecoderFactory.Parameters(location = location)
                ).getOrThrow()

                val buffer = bufferFactory.create(
                    parameters = BufferFactory.Parameters(capacity = audioBufferSize)
                ).onFailure {
                    decoder.close().getOrThrow()

                    throw it
                }.getOrThrow()

                val sampler = samplerFactory.create(
                    parameters = SamplerFactory.Parameters(
                        sampleRate = format.sampleRate, channels = format.channels
                    )
                ).onFailure {
                    buffer.close().getOrThrow()

                    decoder.close().getOrThrow()

                    throw it
                }.getOrThrow()

                Pipeline.Audio(
                    media = media, decoder = decoder, buffer = buffer, sampler = sampler
                )
            }

            is Media.Video -> with(media) {
                val pool = poolFactory.create(
                    parameters = PoolFactory.Parameters(poolCapacity = videoBufferSize, createData = {
                        Data.makeUninitialized(videoFormat.bufferCapacity)
                    })
                ).getOrThrow()

                val decoder = videoDecoderFactory.create(
                    parameters = VideoDecoderFactory.Parameters(
                        location = location, hardwareAccelerationCandidates = hardwareAccelerationCandidates
                    )
                ).onFailure {
                    pool.close().getOrThrow()

                    throw it
                }.getOrThrow()

                val buffer = bufferFactory.create(
                    parameters = BufferFactory.Parameters(capacity = videoBufferSize)
                ).onFailure {
                    decoder.close().getOrThrow()

                    pool.close().getOrThrow()

                    throw it
                }.getOrThrow()

                Pipeline.Video(media = media, decoder = decoder, pool = pool, buffer = buffer)
            }

            is Media.AudioVideo -> with(media) {
                val videoPool = poolFactory.create(
                    parameters = PoolFactory.Parameters(poolCapacity = videoBufferSize, createData = {
                        Data.makeUninitialized(videoFormat.bufferCapacity)
                    })
                ).getOrThrow()

                val audioDecoder = audioDecoderFactory.create(
                    parameters = AudioDecoderFactory.Parameters(location = location)
                ).onFailure {
                    videoPool.close().getOrThrow()

                    throw it
                }.getOrThrow()

                val videoDecoder = videoDecoderFactory.create(
                    parameters = VideoDecoderFactory.Parameters(
                        location = location, hardwareAccelerationCandidates = hardwareAccelerationCandidates
                    )
                ).onFailure {
                    videoPool.close().getOrThrow()

                    audioDecoder.close().getOrThrow()

                    throw it
                }.getOrThrow()

                val audioBuffer = bufferFactory.create(
                    parameters = BufferFactory.Parameters(capacity = audioBufferSize)
                ).onFailure {
                    videoDecoder.close().getOrThrow()

                    audioDecoder.close().getOrThrow()

                    videoPool.close().getOrThrow()

                    throw it
                }.getOrThrow()

                val videoBuffer = bufferFactory.create(
                    parameters = BufferFactory.Parameters(capacity = videoBufferSize)
                ).onFailure {
                    audioBuffer.clear().getOrThrow()

                    videoDecoder.close().getOrThrow()

                    audioDecoder.close().getOrThrow()

                    videoPool.close().getOrThrow()

                    throw it
                }.getOrThrow()

                val sampler = samplerFactory.create(
                    parameters = SamplerFactory.Parameters(
                        sampleRate = audioFormat.sampleRate, channels = audioFormat.channels
                    )
                ).onFailure {
                    videoBuffer.clear().getOrThrow()

                    audioBuffer.clear().getOrThrow()

                    videoDecoder.close().getOrThrow()

                    audioDecoder.close().getOrThrow()

                    videoPool.close().getOrThrow()

                    throw it
                }.getOrThrow()

                Pipeline.AudioVideo(
                    media = media,
                    audioDecoder = audioDecoder,
                    videoDecoder = videoDecoder,
                    videoPool = videoPool,
                    audioBuffer = audioBuffer,
                    videoBuffer = videoBuffer,
                    sampler = sampler
                )
            }
        }

        val bufferLoop = bufferLoopFactory.create(
            parameters = BufferLoopFactory.Parameters(pipeline = pipeline)
        ).onFailure {
            pipeline.close().getOrThrow()

            throw it
        }.getOrThrow()

        val playbackLoop = playbackLoopFactory.create(
            parameters = PlaybackLoopFactory.Parameters(
                pipeline = pipeline,
                syncThreshold = SYNC_THRESHOLD,
                getVolume = { if (settings.value.isMuted) 0f else settings.value.volume },
                getPlaybackSpeedFactor = { settings.value.playbackSpeedFactor },
                getRenderer = { renderer })
        ).onFailure {
            bufferLoop.close().getOrThrow()

            pipeline.close().getOrThrow()

            throw it
        }.getOrThrow()

        renderFirstFrame(pipeline = pipeline)

        bufferTimestamp.emit(Duration.ZERO)

        playbackTimestamp.emit(Duration.ZERO)

        return Triple(pipeline, bufferLoop, playbackLoop)
    }

    private suspend fun InternalPlayerState.Ready.handlePlay() {
        when (val pipeline = pipeline) {
            is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Video -> Unit

            is Pipeline.AudioVideo -> pipeline.sampler.start().getOrThrow()
        }

        playbackLoop.start(
            coroutineScope = playbackScope,
            onException = ::handleException,
            onTimestamp = ::handlePlaybackTimestamp,
            onEndOfMedia = {
                handlePlaybackCompletion()
            }).getOrThrow()

        bufferLoop.start(
            coroutineScope = bufferScope,
            onException = ::handleException,
            onTimestamp = ::handleBufferTimestamp,
            onEndOfMedia = {
                handleBufferCompletion()
            }).getOrThrow()
    }

    private suspend fun InternalPlayerState.Ready.handlePause() {
        playbackLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.Audio -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Video -> Unit

            is Pipeline.AudioVideo -> pipeline.sampler.stop().getOrThrow()
        }
    }

    private suspend fun InternalPlayerState.Ready.handleResume() {
        when (val pipeline = pipeline) {
            is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Video -> Unit

            is Pipeline.AudioVideo -> pipeline.sampler.start().getOrThrow()
        }

        playbackLoop.start(
            coroutineScope = playbackScope,
            onException = ::handleException,
            onTimestamp = ::handlePlaybackTimestamp,
            onEndOfMedia = {
                handlePlaybackCompletion()
            }).getOrThrow()
    }

    private suspend fun InternalPlayerState.Ready.handleStop() {
        playbackLoop.stop().getOrThrow()

        bufferLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.Audio -> with(pipeline) {
                sampler.flush().getOrThrow()

                buffer.clear().getOrThrow()

                decoder.reset().getOrThrow()
            }

            is Pipeline.Video -> with(pipeline) {
                buffer.clear().getOrThrow()

                pool.reset().getOrThrow()

                decoder.reset().getOrThrow()
            }

            is Pipeline.AudioVideo -> with(pipeline) {
                val audioJob = controllerScope.launch {
                    sampler.flush().getOrThrow()

                    audioBuffer.clear().getOrThrow()

                    audioDecoder.reset().getOrThrow()
                }

                val videoJob = controllerScope.launch {
                    videoBuffer.clear().getOrThrow()

                    videoPool.reset().getOrThrow()

                    videoDecoder.reset().getOrThrow()
                }

                joinAll(audioJob, videoJob)
            }
        }

        renderFirstFrame(pipeline = pipeline)

        bufferTimestamp.emit(Duration.ZERO)

        playbackTimestamp.emit(Duration.ZERO)
    }

    private suspend fun InternalPlayerState.Ready.handleSeekTo(
        timestamp: Duration,
        onSeekStart: suspend (suspend () -> Unit) -> Unit,
        onSeekEnd: suspend (suspend () -> Unit) -> Unit,
    ) {
        onSeekStart {
            playbackLoop.stop().getOrThrow()

            bufferLoop.stop().getOrThrow()

            when (val pipeline = pipeline) {
                is Pipeline.Audio -> with(pipeline) {
                    sampler.flush().getOrThrow()

                    buffer.clear().getOrThrow()

                    decoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()
                }

                is Pipeline.Video -> with(pipeline) {
                    buffer.clear().getOrThrow()

                    pool.reset().getOrThrow()

                    decoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()
                }

                is Pipeline.AudioVideo -> with(pipeline) {
                    val audioJob = controllerScope.launch {
                        sampler.flush().getOrThrow()

                        audioBuffer.clear().getOrThrow()

                        audioDecoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()
                    }

                    val videoJob = controllerScope.launch {
                        videoBuffer.clear().getOrThrow()

                        videoPool.reset().getOrThrow()

                        videoDecoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()
                    }

                    joinAll(audioJob, videoJob)
                }
            }

            var timestampAfterSeek = timestamp

            when (val pipeline = pipeline) {
                is Pipeline.Audio -> with(pipeline) {
                    decoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()

                    val frame = decoder.decodeAudio().getOrThrow()

                    buffer.put(item = frame).getOrThrow()

                    (frame as? Frame.Content.Audio)?.timestamp?.let { timestamp ->
                        timestampAfterSeek = timestamp
                    }
                }

                is Pipeline.Video -> with(pipeline) {
                    decoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()

                    val data = pool.acquire().getOrThrow()

                    try {
                        val frame = decoder.decodeVideo(data = data).getOrThrow() as? Frame.Content.Video

                        if (frame != null) {
                            renderer?.render(frame)?.getOrThrow()

                            timestampAfterSeek = frame.timestamp
                        }
                    } finally {
                        pool.release(item = data).getOrThrow()
                    }
                }

                is Pipeline.AudioVideo -> with(pipeline) {
                    val seekAudioJob = controllerScope.launch {
                        audioDecoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()
                    }

                    val seekVideoJob = controllerScope.launch {
                        videoDecoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()
                    }

                    joinAll(seekAudioJob, seekVideoJob)

                    val audioFrame = audioDecoder.decodeAudio().getOrThrow()

                    audioBuffer.put(item = audioFrame).getOrThrow()

                    val audioTimestamp = (audioFrame as? Frame.Content.Audio)?.timestamp

                    var videoTimestamp: Duration? = null

                    controllerScope.launch {
                        while (isActive) {
                            val data = videoPool.acquire().getOrThrow()

                            try {
                                val videoFrame = videoDecoder.decodeVideo(
                                    data = data
                                ).getOrThrow() as? Frame.Content.Video ?: break

                                videoTimestamp = videoFrame.timestamp

                                when {
                                    audioTimestamp == null -> break

                                    videoFrame.timestamp - audioTimestamp < -SYNC_THRESHOLD -> continue
                                }

                                renderer?.render(videoFrame)?.getOrThrow()

                                break
                            } finally {
                                videoPool.release(item = data).getOrThrow()
                            }
                        }
                    }.join()

                    timestampAfterSeek = listOfNotNull(audioTimestamp, videoTimestamp).max()
                }
            }

            bufferTimestamp.emit(timestampAfterSeek)

            playbackTimestamp.emit(timestampAfterSeek)
        }

        onSeekEnd {
            bufferLoop.start(
                coroutineScope = bufferScope,
                onException = ::handleException,
                onTimestamp = ::handleBufferTimestamp,
                onEndOfMedia = {
                    handleBufferCompletion()
                }).getOrThrow()
        }
    }

    private suspend fun InternalPlayerState.Ready.handleRelease() {
        playbackLoop.close().getOrThrow()

        bufferLoop.close().getOrThrow()

        pipeline.close().getOrThrow()

        bufferTimestamp.emit(Duration.ZERO)

        playbackTimestamp.emit(Duration.ZERO)
    }

    private val _events = Channel<PlayerEvent>(Channel.BUFFERED)

    override val events = _events.receiveAsFlow()

    override suspend fun attachRenderer(renderer: Renderer) = runCatching {
        check(this.renderer == null) { "Detach the renderer before attaching a new one" }

        this.renderer = renderer
    }

    override suspend fun detachRenderer() = runCatching {
        val previousRenderer = renderer

        renderer = null

        previousRenderer
    }

    override suspend fun changeSettings(newSettings: PlayerSettings) = commandMutex.withLock {
        runCatching {
            require(newSettings.playbackSpeedFactor in MIN_PLAYBACK_SPEED_FACTOR..MAX_PLAYBACK_SPEED_FACTOR) {
                "Invalid playback speed factor"
            }

            settings.emit(newSettings)
        }
    }

    override suspend fun resetSettings() = commandMutex.withLock {
        runCatching {
            val newSettings = initialSettings ?: defaultSettings

            settings.emit(newSettings)
        }
    }

    override suspend fun execute(command: Command): Result<Unit> = commandMutex.withLock {
        runCatching {
            when (command) {
                is Command.Prepare -> when (val state = internalState.value) {
                    is InternalPlayerState.Empty, is InternalPlayerState.Error -> with(command) {
                        updateInternalState(InternalPlayerState.Preparing)

                        try {
                            val (pipeline, bufferLoop, playbackLoop) = handlePrepare(
                                location = location,
                                audioBufferSize = audioBufferSize,
                                videoBufferSize = videoBufferSize,
                                hardwareAccelerationCandidates = hardwareAccelerationCandidates
                            )

                            updateInternalState(
                                InternalPlayerState.Ready.Stopped(
                                    media = pipeline.media,
                                    pipeline = pipeline,
                                    bufferLoop = bufferLoop,
                                    playbackLoop = playbackLoop,
                                    previousState = null
                                )
                            )
                        } catch (t: Throwable) {
                            updateInternalState(InternalPlayerState.Error(cause = t, previous = state))
                        }
                    }

                    else -> return@runCatching
                }

                is Command.Play -> when (val state = internalState.value) {
                    is InternalPlayerState.Ready.Stopped -> with(state) {
                        if (!media.isContinuous()) {
                            return@runCatching
                        }

                        withTransition(Destination.PLAYING) {
                            handlePlay()
                        }
                    }

                    else -> return@runCatching
                }

                is Command.Pause -> when (val state = internalState.value) {
                    is InternalPlayerState.Ready.Playing -> with(state) {
                        if (!media.isContinuous()) {
                            return@runCatching
                        }

                        withTransition(Destination.PAUSED) {
                            handlePause()
                        }
                    }

                    else -> return@runCatching
                }

                is Command.Resume -> when (val state = internalState.value) {
                    is InternalPlayerState.Ready.Paused -> with(state) {
                        if (!media.isContinuous()) {
                            return@runCatching
                        }

                        withTransition(Destination.PLAYING) {
                            handleResume()
                        }
                    }

                    else -> return@runCatching
                }

                is Command.Stop -> when (val state = internalState.value) {
                    is InternalPlayerState.Ready -> when (state) {
                        is InternalPlayerState.Ready.Playing,
                        is InternalPlayerState.Ready.Paused,
                        is InternalPlayerState.Ready.Completed,
                        is InternalPlayerState.Ready.Seeking,
                            -> with(state) {
                            if (!media.isContinuous()) {
                                return@runCatching
                            }

                            withTransition(Destination.STOPPED) {
                                handleStop()
                            }
                        }

                        else -> return@runCatching
                    }

                    else -> return@runCatching
                }

                is Command.SeekTo -> when (val state = internalState.value) {
                    is InternalPlayerState.Ready -> when (state) {
                        is InternalPlayerState.Ready.Playing,
                        is InternalPlayerState.Ready.Paused,
                        is InternalPlayerState.Ready.Stopped,
                        is InternalPlayerState.Ready.Completed
                            -> with(state) {
                            if (!media.isContinuous()) {
                                return@runCatching
                            }

                            handleSeekTo(timestamp = command.timestamp, onSeekStart = { block ->
                                withTransition(Destination.SEEKING) {
                                    block()
                                }
                            }, onSeekEnd = { block ->
                                val stateAfterSeek = internalState.value

                                check(stateAfterSeek is InternalPlayerState.Ready) { "Invalid state after seek" }

                                stateAfterSeek.withTransition(Destination.PAUSED) {
                                    block()
                                }
                            })
                        }

                        else -> return@runCatching
                    }

                    else -> return@runCatching
                }

                is Command.Release -> when (val state = internalState.value) {
                    is InternalPlayerState.Ready -> {
                        updateInternalState(InternalPlayerState.Releasing(previousState = state))

                        state.handleRelease()

                        updateInternalState(InternalPlayerState.Empty)
                    }

                    else -> return@runCatching
                }
            }
        }
    }

    override suspend fun close() = commandMutex.withLock {
        runCatching {
            controllerScope.cancel()

            when (val currentState = internalState.value) {
                is InternalPlayerState.Empty,
                is InternalPlayerState.Preparing,
                is InternalPlayerState.Releasing,
                is InternalPlayerState.Error,
                    -> Unit

                is InternalPlayerState.Ready -> with(currentState) {
                    playbackLoop.close().getOrThrow()

                    bufferLoop.close().getOrThrow()

                    pipeline.close().getOrThrow()
                }
            }
        }
    }
}