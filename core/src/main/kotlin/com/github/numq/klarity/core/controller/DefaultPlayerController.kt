package com.github.numq.klarity.core.controller

import com.github.numq.klarity.core.buffer.BufferFactory
import com.github.numq.klarity.core.command.Command
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.pool.PoolFactory
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.Sampler
import com.github.numq.klarity.core.sampler.SamplerFactory
import com.github.numq.klarity.core.settings.PlayerSettings
import com.github.numq.klarity.core.state.InternalPlayerState
import com.github.numq.klarity.core.state.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration

internal class DefaultPlayerController(
    private val initialSettings: PlayerSettings?,
    private val audioDecoderFactory: AudioDecoderFactory,
    private val videoDecoderFactory: VideoDecoderFactory,
    private val poolFactory: PoolFactory,
    private val bufferFactory: BufferFactory,
    private val bufferLoopFactory: BufferLoopFactory,
    private val playbackLoopFactory: PlaybackLoopFactory,
    private val samplerFactory: SamplerFactory
) : PlayerController {
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

    override fun attachRenderer(renderer: Renderer) {
        this.renderer = renderer
    }

    override fun detachRenderer() {
        renderer = null
    }

    /**
     * Settings
     */

    private val defaultSettings = PlayerSettings(
        playbackSpeedFactor = 1f, isMuted = false, volume = 1f
    )

    override val settings = MutableStateFlow(initialSettings ?: defaultSettings)

    private suspend fun Sampler.applySettings(settings: PlayerSettings) {
        setVolume(value = settings.volume).getOrThrow()

        setMuted(state = settings.isMuted).getOrThrow()

        setPlaybackSpeed(factor = settings.playbackSpeedFactor).getOrThrow()
    }

    private suspend fun applySettings(pipeline: Pipeline, settings: PlayerSettings) = runCatching {
        when (pipeline) {
            is Pipeline.Audio -> pipeline.sampler.applySettings(settings)

            is Pipeline.Video -> Unit

            is Pipeline.AudioVideo -> pipeline.sampler.applySettings(settings)
        }
    }

    /**
     * State
     */

    private var internalState: InternalPlayerState = InternalPlayerState.Empty

    override val state = MutableStateFlow<PlayerState>(PlayerState.Empty)

    private suspend fun <T> withInternalState(
        onEmpty: (suspend InternalPlayerState.Empty.() -> T)? = null,
        onPreparing: (suspend InternalPlayerState.Preparing.() -> T)? = null,
        onReady: (suspend InternalPlayerState.Ready.() -> T)? = null
    ) = when (val state = internalState) {
        is InternalPlayerState.Empty -> onEmpty?.invoke(state)

        is InternalPlayerState.Preparing -> onPreparing?.invoke(state)

        is InternalPlayerState.Ready -> onReady?.invoke(state)
    }

    private suspend fun updateState(newState: InternalPlayerState) {
        if (internalState == newState) return

        internalState = newState

        when (newState) {
            is InternalPlayerState.Empty -> state.emit(PlayerState.Empty)

            is InternalPlayerState.Preparing -> Unit

            is InternalPlayerState.Ready -> when (newState.status) {
                InternalPlayerState.Ready.Status.TRANSITION -> Unit

                InternalPlayerState.Ready.Status.PLAYING -> state.emit(PlayerState.Ready.Playing(media = newState.media))

                InternalPlayerState.Ready.Status.PAUSED -> state.emit(PlayerState.Ready.Paused(media = newState.media))

                InternalPlayerState.Ready.Status.STOPPED -> state.emit(PlayerState.Ready.Stopped(media = newState.media))

                InternalPlayerState.Ready.Status.COMPLETED -> state.emit(PlayerState.Ready.Completed(media = newState.media))

                InternalPlayerState.Ready.Status.SEEKING -> state.emit(PlayerState.Ready.Seeking(media = newState.media))

                InternalPlayerState.Ready.Status.RELEASING -> Unit
            }
        }
    }

    /**
     * Timestamp
     */

    override val bufferTimestamp = MutableStateFlow(Duration.ZERO)

    override val playbackTimestamp = MutableStateFlow(Duration.ZERO)

    private suspend fun handleBufferTimestamp(timestamp: Duration) {
        (internalState as? InternalPlayerState.Ready)?.status?.let { status ->
            if (status == InternalPlayerState.Ready.Status.PLAYING || status == InternalPlayerState.Ready.Status.PAUSED) {
                bufferTimestamp.emit(timestamp)
            }
        }
    }

    private suspend fun handlePlaybackTimestamp(timestamp: Duration) {
        (internalState as? InternalPlayerState.Ready)?.status?.let { status ->
            if (status == InternalPlayerState.Ready.Status.PLAYING) {
                playbackTimestamp.emit(timestamp)
            }
        }
    }

    /**
     * Events
     */

    private suspend fun handleException(throwable: Throwable) {
        events.emit(PlayerEvent.Error((throwable as? Exception) ?: Exception(throwable)))

        (internalState as? InternalPlayerState.Ready)?.handleRelease()
    }

    /**
     * Completion
     */

    private suspend fun InternalPlayerState.Ready.handleBufferCompletion() {
        bufferLoop.stop().getOrThrow()

        bufferTimestamp.emit(media.duration)

        events.emit(PlayerEvent.Buffer.Complete)
    }

    private suspend fun InternalPlayerState.Ready.handlePlaybackCompletion() {
        when (val pipeline = pipeline) {
            is Pipeline.Audio -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Video -> Unit

            is Pipeline.AudioVideo -> pipeline.sampler.stop().getOrThrow()
        }

        playbackTimestamp.emit(media.duration)

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.COMPLETED))
    }

    /**
     * Command
     */

    private val commandMutex = Mutex()

    private var commandJob: Job? = null

    private suspend fun executeMediaCommand(command: suspend () -> Unit) = commandMutex.withLock { command() }

    private suspend fun InternalPlayerState.Ready.executePlaybackCommand(
        command: suspend InternalPlayerState.Ready.() -> Unit,
    ) = commandMutex.withLock {
        check(internalState is InternalPlayerState.Ready) { "Unable to execute playback command" }

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.TRANSITION))

        when (val currentInternalState = internalState) {
            is InternalPlayerState.Ready -> if (currentInternalState.media.duration.isPositive()) {
                command(currentInternalState)
            }

            else -> error("Unable to execute playback command")
        }
    }

    private suspend fun handlePrepare(
        location: String,
        audioBufferSize: Int,
        videoBufferSize: Int,
        hardwareAccelerationCandidates: List<HardwareAcceleration>?
    ) = executeMediaCommand {
        updateState(InternalPlayerState.Preparing)

        val media = Decoder.probe(
            location = location, findAudioStream = audioBufferSize > 0, findVideoStream = videoBufferSize > 0
        ).getOrThrow()

        check(!media.duration.isNegative()) { "Media does not support playback" }

        try {
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
                            sampleRate = format.sampleRate,
                            channels = format.channels
                        )
                    ).onFailure {
                        buffer.close().getOrThrow()

                        decoder.close().getOrThrow()

                        throw it
                    }.getOrThrow()

                    Pipeline.Audio(
                        media = media,
                        decoder = decoder,
                        buffer = buffer,
                        sampler = sampler
                    )
                }

                is Media.Video -> with(media) {
                    val pool = poolFactory.create(
                        parameters = PoolFactory.Parameters(
                            poolCapacity = videoBufferSize,
                            bufferCapacity = media.format.bufferCapacity
                        )
                    ).getOrThrow()

                    val decoder = videoDecoderFactory.create(
                        parameters = VideoDecoderFactory.Parameters(
                            location = location,
                            hardwareAccelerationCandidates = hardwareAccelerationCandidates
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

                    Pipeline.Video(
                        media = media,
                        decoder = decoder,
                        pool = pool,
                        buffer = buffer
                    )
                }

                is Media.AudioVideo -> with(media) {
                    val videoPool = poolFactory.create(
                        parameters = PoolFactory.Parameters(
                            poolCapacity = videoBufferSize,
                            bufferCapacity = media.videoFormat.bufferCapacity
                        )
                    ).getOrThrow()


                    val audioDecoder = audioDecoderFactory.create(
                        parameters = AudioDecoderFactory.Parameters(location = location)
                    ).onFailure {
                        videoPool.close().getOrThrow()

                        throw it
                    }.getOrThrow()

                    val videoDecoder = videoDecoderFactory.create(
                        parameters = VideoDecoderFactory.Parameters(
                            location = location,
                            hardwareAccelerationCandidates = hardwareAccelerationCandidates
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

            applySettings(pipeline, settings.value).getOrThrow()

            val bufferLoop = bufferLoopFactory.create(
                parameters = BufferLoopFactory.Parameters(pipeline = pipeline)
            ).onFailure {
                pipeline.close().getOrThrow()

                throw it
            }.getOrThrow()

            val playbackLoop = playbackLoopFactory.create(
                parameters = PlaybackLoopFactory.Parameters(
                    pipeline = pipeline,
                    getPlaybackSpeedFactor = { settings.value.playbackSpeedFactor.toDouble() },
                    getRenderer = { renderer })
            ).onFailure {
                bufferLoop.close().getOrThrow()

                pipeline.close().getOrThrow()

                throw it
            }.getOrThrow()

            updateState(
                InternalPlayerState.Ready(
                    media = pipeline.media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop,
                    status = InternalPlayerState.Ready.Status.STOPPED
                )
            )
        } catch (t: Throwable) {
            updateState(InternalPlayerState.Empty)

            throw t
        }
    }

    private suspend fun InternalPlayerState.Ready.handlePlay() = executePlaybackCommand {
        when (val pipeline = pipeline) {
            is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Video -> Unit

            is Pipeline.AudioVideo -> pipeline.sampler.start().getOrThrow()
        }

        playbackLoop.start(
            coroutineScope = playbackScope,
            onException = ::handleException,
            onTimestamp = ::handlePlaybackTimestamp,
            onEndOfMedia = { handlePlaybackCompletion() }).getOrThrow()

        bufferLoop.start(
            coroutineScope = bufferScope,
            onException = ::handleException,
            onTimestamp = ::handleBufferTimestamp,
            onEndOfMedia = { handleBufferCompletion() }).getOrThrow()

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PLAYING))
    }

    private suspend fun InternalPlayerState.Ready.handlePause() = executePlaybackCommand {
        playbackLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.Audio -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Video -> Unit

            is Pipeline.AudioVideo -> pipeline.sampler.stop().getOrThrow()

        }

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PAUSED))
    }

    private suspend fun InternalPlayerState.Ready.handleResume() = executePlaybackCommand {
        when (val pipeline = pipeline) {
            is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Video -> Unit

            is Pipeline.AudioVideo -> pipeline.sampler.start().getOrThrow()
        }

        playbackLoop.start(
            coroutineScope = playbackScope,
            onException = ::handleException,
            onTimestamp = ::handlePlaybackTimestamp,
            onEndOfMedia = { handlePlaybackCompletion() }).getOrThrow()

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PLAYING))
    }

    private suspend fun InternalPlayerState.Ready.handleStop() = executePlaybackCommand {
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

        bufferTimestamp.emit(Duration.ZERO)

        playbackTimestamp.emit(Duration.ZERO)

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.STOPPED))
    }

    private suspend fun InternalPlayerState.Ready.handleSeekTo(
        timestamp: Duration, keyFramesOnly: Boolean
    ) = executePlaybackCommand {
        playbackLoop.stop().getOrThrow()

        bufferLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.Audio -> with(pipeline) {
                sampler.flush().getOrThrow()

                buffer.clear().getOrThrow()
            }

            is Pipeline.Video -> with(pipeline) {
                buffer.clear().getOrThrow()

                pool.reset().getOrThrow()
            }

            is Pipeline.AudioVideo -> with(pipeline) {
                sampler.flush().getOrThrow()

                audioBuffer.clear().getOrThrow()

                videoBuffer.clear().getOrThrow()

                videoPool.reset().getOrThrow()
            }
        }

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.SEEKING))

        var timestampAfterSeek = timestamp

        when (val pipeline = pipeline) {
            is Pipeline.Audio -> with(pipeline) {
                timestampAfterSeek = decoder.seekTo(
                    timestamp = timestamp, keyframesOnly = keyFramesOnly
                ).getOrThrow()
            }

            is Pipeline.Video -> with(pipeline) {
                timestampAfterSeek = decoder.seekTo(
                    timestamp = timestamp, keyframesOnly = keyFramesOnly
                ).getOrThrow()
            }

            is Pipeline.AudioVideo -> with(pipeline) {
                val seekAudioJob = controllerScope.async {
                    audioDecoder.seekTo(timestamp = timestamp, keyframesOnly = keyFramesOnly).getOrThrow()
                }

                val seekVideoJob = controllerScope.async {
                    videoDecoder.seekTo(timestamp = timestamp, keyframesOnly = keyFramesOnly).getOrThrow()
                }

                timestampAfterSeek = maxOf(seekAudioJob.await(), seekVideoJob.await())
            }
        }

        bufferLoop.start(
            coroutineScope = bufferScope,
            onException = ::handleException,
            onTimestamp = ::handleBufferTimestamp,
            onEndOfMedia = { handleBufferCompletion() }).getOrThrow()

        bufferTimestamp.emit(timestampAfterSeek)

        playbackTimestamp.emit(timestampAfterSeek)

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PAUSED))
    }

    private suspend fun InternalPlayerState.Ready.handleRelease() = executeMediaCommand {
        updateState(updateStatus(status = InternalPlayerState.Ready.Status.RELEASING))

        playbackLoop.close().getOrThrow()

        bufferLoop.close().getOrThrow()

        pipeline.close().getOrThrow()

        bufferTimestamp.emit(Duration.ZERO)

        playbackTimestamp.emit(Duration.ZERO)

        updateState(InternalPlayerState.Empty)
    }

    override val events = MutableSharedFlow<PlayerEvent>()

    override suspend fun changeSettings(newSettings: PlayerSettings) = commandMutex.withLock {
        runCatching {
            (internalState as? InternalPlayerState.Ready)?.run {
                applySettings(pipeline, newSettings).getOrThrow()
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

    override suspend fun execute(command: Command) = runCatching {
        val status = (internalState as? InternalPlayerState.Ready)?.status

        commandJob = when (command) {
            is Command.Prepare -> controllerScope.launch {
                withInternalState(onEmpty = {
                    with(command) {
                        handlePrepare(
                            location = location,
                            audioBufferSize = audioBufferSize,
                            videoBufferSize = videoBufferSize,
                            hardwareAccelerationCandidates = hardwareAccelerationCandidates
                        )
                    }
                })
            }

            is Command.Play -> controllerScope.launch {
                withInternalState {
                    if (status == InternalPlayerState.Ready.Status.STOPPED) {
                        handlePlay()
                    }
                }
            }

            is Command.Pause -> controllerScope.launch {
                withInternalState {
                    if (status == InternalPlayerState.Ready.Status.PLAYING) {
                        handlePause()
                    }
                }
            }

            is Command.Resume -> controllerScope.launch {
                withInternalState {
                    if (status == InternalPlayerState.Ready.Status.PAUSED) {
                        handleResume()
                    }
                }
            }

            is Command.Stop -> controllerScope.launch {
                withInternalState {
                    when (status) {
                        InternalPlayerState.Ready.Status.PLAYING, InternalPlayerState.Ready.Status.PAUSED, InternalPlayerState.Ready.Status.COMPLETED, InternalPlayerState.Ready.Status.SEEKING -> handleStop()

                        else -> return@withInternalState
                    }
                }
            }

            is Command.SeekTo -> controllerScope.launch {
                withInternalState {
                    when (status) {
                        InternalPlayerState.Ready.Status.PLAYING, InternalPlayerState.Ready.Status.PAUSED, InternalPlayerState.Ready.Status.STOPPED, InternalPlayerState.Ready.Status.COMPLETED, InternalPlayerState.Ready.Status.SEEKING -> handleSeekTo(
                            timestamp = command.timestamp, keyFramesOnly = command.keyFramesOnly
                        )

                        else -> return@withInternalState
                    }
                }
            }

            is Command.Release -> withInternalState {
                commandJob?.cancelAndJoin()

                commandJob = null

                controllerScope.launch {
                    handleRelease()
                }
            }
        }

        commandJob?.join()

        Unit
    }

    override suspend fun close() = commandMutex.withLock {
        runCatching {
            controllerScope.cancel()

            when (val currentState = internalState) {
                is InternalPlayerState.Empty,
                is InternalPlayerState.Preparing,
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