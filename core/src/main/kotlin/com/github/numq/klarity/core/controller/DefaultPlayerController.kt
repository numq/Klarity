package com.github.numq.klarity.core.controller

import com.github.numq.klarity.core.buffer.AudioBufferFactory
import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.buffer.VideoBufferFactory
import com.github.numq.klarity.core.command.Command
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoop
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
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
    private val audioDecoderFactory: Factory<AudioDecoderFactory.Parameters, Decoder<Media.Audio, Frame.Audio>>,
    private val videoDecoderFactory: Factory<VideoDecoderFactory.Parameters, Decoder<Media.Video, Frame.Video>>,
    private val audioBufferFactory: Factory<AudioBufferFactory.Parameters, Buffer<Frame.Audio>>,
    private val videoBufferFactory: Factory<VideoBufferFactory.Parameters, Buffer<Frame.Video>>,
    private val bufferLoopFactory: Factory<BufferLoopFactory.Parameters, BufferLoop>,
    private val playbackLoopFactory: Factory<PlaybackLoopFactory.Parameters, PlaybackLoop>,
    private val samplerFactory: Factory<SamplerFactory.Parameters, Sampler>
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
        setVolume(value = settings.volume).getOrDefault(Unit)

        setMuted(state = settings.isMuted).getOrDefault(Unit)

        setPlaybackSpeed(factor = settings.playbackSpeedFactor).getOrDefault(Unit)
    }

    private suspend fun applySettings(pipeline: Pipeline, settings: PlayerSettings) = runCatching {
        when (pipeline) {
            is Pipeline.AudioVideo -> pipeline.sampler.applySettings(settings)

            is Pipeline.Audio -> pipeline.sampler.applySettings(settings)

            is Pipeline.Video -> Unit
        }
    }

    /**
     * State
     */

    private var internalState: InternalPlayerState = InternalPlayerState.Empty

    override val state = MutableStateFlow<PlayerState>(PlayerState.Empty)

    private suspend fun withInternalState(
        onEmpty: (suspend InternalPlayerState.Empty.() -> Unit)? = null,
        onPreparing: (suspend InternalPlayerState.Preparing.() -> Unit)? = null,
        onReady: (suspend InternalPlayerState.Ready.() -> Unit)? = null
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
            is Pipeline.AudioVideo -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Video -> Unit
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
        sampleRate: Int?,
        channels: Int?,
        width: Int?,
        height: Int?,
        frameRate: Double?,
        hardwareAccelerationCandidates: List<HardwareAcceleration>?
    ) = executeMediaCommand {
        updateState(InternalPlayerState.Preparing)

        val media = Decoder.probe(
            location = location, findAudioStream = audioBufferSize > 0, findVideoStream = videoBufferSize > 0
        ).getOrThrow()

        val pipeline = when (media) {
            is Media.Audio -> with(media) {
                val decoder = audioDecoderFactory.create(
                    parameters = AudioDecoderFactory.Parameters(
                        location = location, sampleRate = sampleRate, channels = channels
                    )
                ).getOrThrow()

                val buffer = audioBufferFactory.create(
                    parameters = AudioBufferFactory.Parameters(capacity = audioBufferSize)
                ).getOrThrow()

                val sampler = samplerFactory.create(
                    parameters = SamplerFactory.Parameters(sampleRate = format.sampleRate, channels = format.channels)
                ).getOrThrow()

                Pipeline.Audio(media = media, decoder = decoder, buffer = buffer, sampler = sampler)
            }

            is Media.Video -> with(media) {
                val decoder = videoDecoderFactory.create(
                    parameters = VideoDecoderFactory.Parameters(
                        location = location,
                        width = width,
                        height = height,
                        frameRate = frameRate,
                        hardwareAccelerationCandidates = hardwareAccelerationCandidates
                    )
                ).getOrThrow()

                val buffer = videoBufferFactory.create(
                    parameters = VideoBufferFactory.Parameters(capacity = videoBufferSize)
                ).getOrThrow()

                Pipeline.Video(media = media, decoder = decoder, buffer = buffer)
            }

            is Media.AudioVideo -> with(media) {
                val audioDecoder = audioDecoderFactory.create(
                    parameters = AudioDecoderFactory.Parameters(
                        location = location, sampleRate = sampleRate, channels = channels
                    )
                ).getOrThrow()

                val audioBuffer = audioBufferFactory.create(
                    parameters = AudioBufferFactory.Parameters(capacity = audioBufferSize)
                ).getOrThrow()

                val sampler = samplerFactory.create(
                    parameters = SamplerFactory.Parameters(
                        sampleRate = audioFormat.sampleRate, channels = audioFormat.channels
                    )
                ).getOrThrow()

                val videoDecoder = videoDecoderFactory.create(
                    parameters = VideoDecoderFactory.Parameters(
                        location = location,
                        width = width,
                        height = height,
                        frameRate = frameRate,
                        hardwareAccelerationCandidates = hardwareAccelerationCandidates
                    )
                ).getOrThrow()

                val videoBuffer = videoBufferFactory.create(
                    parameters = VideoBufferFactory.Parameters(capacity = videoBufferSize)
                ).getOrThrow()

                Pipeline.AudioVideo(
                    media = media,
                    audioDecoder = audioDecoder,
                    videoDecoder = videoDecoder,
                    audioBuffer = audioBuffer,
                    videoBuffer = videoBuffer,
                    sampler = sampler
                )
            }
        }

        applySettings(pipeline, settings.value).getOrThrow()

        val bufferLoop = bufferLoopFactory.create(
            parameters = BufferLoopFactory.Parameters(pipeline = pipeline)
        ).getOrThrow()

        val playbackLoop = playbackLoopFactory.create(
            parameters = PlaybackLoopFactory.Parameters(
                pipeline = pipeline,
                getPlaybackSpeedFactor = { settings.value.playbackSpeedFactor.toDouble() },
                getRenderer = { renderer })
        ).getOrThrow()

        updateState(
            InternalPlayerState.Ready(
                media = media,
                pipeline = pipeline,
                bufferLoop = bufferLoop,
                playbackLoop = playbackLoop,
                status = InternalPlayerState.Ready.Status.STOPPED
            )
        )
    }

    private suspend fun InternalPlayerState.Ready.handlePlay() = executePlaybackCommand {
        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Video -> Unit
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
            is Pipeline.AudioVideo -> pipeline.sampler.pause().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.pause().getOrThrow()

            is Pipeline.Video -> Unit
        }

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PAUSED))
    }

    private suspend fun InternalPlayerState.Ready.handleResume() = executePlaybackCommand {
        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Video -> Unit
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
            is Pipeline.AudioVideo -> with(pipeline) {
                sampler.stop().getOrThrow()
                audioBuffer.flush().getOrThrow()
                videoBuffer.flush().getOrThrow()
                audioDecoder.reset().getOrThrow()
                videoDecoder.reset().getOrThrow()
            }

            is Pipeline.Audio -> with(pipeline) {
                sampler.stop().getOrThrow()
                buffer.flush().getOrThrow()
                decoder.reset().getOrThrow()
            }

            is Pipeline.Video -> with(pipeline) {
                buffer.flush().getOrThrow()
                decoder.reset().getOrThrow()
            }
        }

        joinAll(controllerScope.launch {
            bufferTimestamp.emit(Duration.ZERO)
        }, controllerScope.launch {
            playbackTimestamp.emit(Duration.ZERO)
        })

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.STOPPED))
    }

    private suspend fun InternalPlayerState.Ready.handleSeekTo(
        timestamp: Duration, keyFramesOnly: Boolean
    ) = executePlaybackCommand {
        playbackLoop.stop().getOrThrow()

        bufferLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> with(pipeline) {
                sampler.stop().getOrThrow()

                joinAll(controllerScope.launch {
                    audioBuffer.flush().getOrThrow()
                }, controllerScope.launch {
                    videoBuffer.flush().getOrThrow()
                })
            }

            is Pipeline.Audio -> with(pipeline) {
                sampler.stop().getOrThrow()

                buffer.flush().getOrThrow()
            }

            is Pipeline.Video -> pipeline.buffer.flush().getOrThrow()
        }

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.SEEKING))

        var timestampAfterSeek = timestamp

        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> with(pipeline) {
                joinAll(controllerScope.launch {
                    audioDecoder.seekTo(
                        timestamp = timestamp, keyframesOnly = keyFramesOnly
                    ).getOrThrow()
                }, controllerScope.launch {
                    timestampAfterSeek = videoDecoder.seekTo(
                        timestamp = timestamp, keyframesOnly = keyFramesOnly
                    ).getOrThrow()
                })
            }

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
        }

        joinAll(controllerScope.launch {
            bufferTimestamp.emit(Duration.ZERO)
        }, controllerScope.launch {
            playbackTimestamp.emit(Duration.ZERO)
        })

        bufferLoop.start(
            coroutineScope = bufferScope,
            onException = ::handleException,
            onTimestamp = ::handleBufferTimestamp,
            onEndOfMedia = { handleBufferCompletion() }).getOrThrow()

        joinAll(controllerScope.launch {
            bufferTimestamp.emit(timestampAfterSeek)
        }, controllerScope.launch {
            playbackTimestamp.emit(timestampAfterSeek)
        })

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PAUSED))
    }

    private suspend fun InternalPlayerState.Ready.handleRelease() = executeMediaCommand {
        updateState(updateStatus(status = InternalPlayerState.Ready.Status.RELEASING))

        playbackLoop.close().getOrThrow()

        bufferLoop.close().getOrThrow()

        pipeline.close().getOrThrow()

        joinAll(controllerScope.launch {
            bufferTimestamp.emit(Duration.ZERO)
        }, controllerScope.launch {
            playbackTimestamp.emit(Duration.ZERO)
        })

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

        commandJob = controllerScope.launch {
            when (command) {
                is Command.Prepare -> withInternalState(onEmpty = {
                    with(command) {
                        handlePrepare(
                            location = location,
                            audioBufferSize = audioBufferSize,
                            videoBufferSize = videoBufferSize,
                            sampleRate = sampleRate,
                            channels = channels,
                            width = width,
                            height = height,
                            frameRate = frameRate,
                            hardwareAccelerationCandidates = hardwareAccelerationCandidates
                        )
                    }
                })

                is Command.Play -> withInternalState {
                    if (status == InternalPlayerState.Ready.Status.STOPPED) {
                        handlePlay()
                    }
                }

                is Command.Pause -> withInternalState {
                    if (status == InternalPlayerState.Ready.Status.PLAYING) {
                        handlePause()
                    }
                }

                is Command.Resume -> withInternalState {
                    if (status == InternalPlayerState.Ready.Status.PAUSED) {
                        handleResume()
                    }
                }

                is Command.Stop -> withInternalState {
                    when (status) {
                        InternalPlayerState.Ready.Status.PLAYING, InternalPlayerState.Ready.Status.PAUSED, InternalPlayerState.Ready.Status.COMPLETED, InternalPlayerState.Ready.Status.SEEKING -> handleStop()

                        else -> return@withInternalState
                    }
                }

                is Command.SeekTo -> withInternalState {
                    when (status) {
                        InternalPlayerState.Ready.Status.PLAYING, InternalPlayerState.Ready.Status.PAUSED, InternalPlayerState.Ready.Status.STOPPED, InternalPlayerState.Ready.Status.COMPLETED, InternalPlayerState.Ready.Status.SEEKING -> handleSeekTo(
                            timestamp = command.timestamp, keyFramesOnly = command.keyFramesOnly
                        )

                        else -> return@withInternalState
                    }
                }

                is Command.Release -> withInternalState {
                    commandJob?.cancelAndJoin()

                    commandJob = null

                    handleRelease()
                }
            }
        }.also {
            it.join()
        }
    }

    override suspend fun close() = commandMutex.withLock {
        runCatching {
            controllerScope.cancel()

            when (val currentState = internalState) {
                is InternalPlayerState.Empty,
                is InternalPlayerState.Preparing,
                    -> Unit

                is InternalPlayerState.Ready -> with(currentState) {
                    playbackLoop.close().getOrDefault(Unit)
                    bufferLoop.close().getOrDefault(Unit)
                    pipeline.close().getOrDefault(Unit)
                }
            }
        }
    }
}