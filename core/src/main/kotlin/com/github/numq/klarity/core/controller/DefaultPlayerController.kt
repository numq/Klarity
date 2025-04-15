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
import com.github.numq.klarity.core.factory.SuspendFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoop
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pipeline.Pipeline
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.renderer.RendererFactory
import com.github.numq.klarity.core.sampler.Sampler
import com.github.numq.klarity.core.sampler.SamplerFactory
import com.github.numq.klarity.core.settings.PlayerSettings
import com.github.numq.klarity.core.state.InternalPlayerState
import com.github.numq.klarity.core.state.PlayerState
import com.github.numq.klarity.core.timestamp.Timestamp
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultPlayerController(
    private val initialSettings: PlayerSettings?,
    private val audioDecoderFactory: SuspendFactory<AudioDecoderFactory.Parameters, Decoder<Media.Audio, Frame.Audio>>,
    private val videoDecoderFactory: SuspendFactory<VideoDecoderFactory.Parameters, Decoder<Media.Video, Frame.Video>>,
    private val audioBufferFactory: Factory<AudioBufferFactory.Parameters, Buffer<Frame.Audio>>,
    private val videoBufferFactory: Factory<VideoBufferFactory.Parameters, Buffer<Frame.Video>>,
    private val bufferLoopFactory: Factory<BufferLoopFactory.Parameters, BufferLoop>,
    private val playbackLoopFactory: Factory<PlaybackLoopFactory.Parameters, PlaybackLoop>,
    private val samplerFactory: SuspendFactory<SamplerFactory.Parameters, Sampler>,
    private val rendererFactory: Factory<RendererFactory.Parameters, Renderer>,
) : PlayerController {
    companion object {
        const val MIN_AUDIO_BUFFER_SIZE = 1
        const val MIN_VIDEO_BUFFER_SIZE = 1
    }

    /**
     * Settings
     */

    private val defaultSettings = PlayerSettings(
        playbackSpeedFactor = 1f,
        isMuted = false,
        volume = 1f,
        audioBufferSize = MIN_AUDIO_BUFFER_SIZE,
        videoBufferSize = MIN_VIDEO_BUFFER_SIZE
    )

    override val settings = MutableStateFlow(initialSettings ?: defaultSettings)

    private suspend fun Sampler.applySettings(settings: PlayerSettings) {
        setVolume(value = settings.volume).getOrDefault(Unit)
        setMuted(state = settings.isMuted).getOrDefault(Unit)
        setPlaybackSpeed(factor = settings.playbackSpeedFactor).getOrDefault(Unit)
    }

    private suspend fun Renderer.applySettings(settings: PlayerSettings) {
        setPlaybackSpeed(factor = settings.playbackSpeedFactor).getOrDefault(Unit)
    }

    private suspend fun applySettings(pipeline: Pipeline, settings: PlayerSettings) = runCatching {
        when (pipeline) {
            is Pipeline.AudioVideo -> {
                pipeline.sampler.applySettings(settings)
                pipeline.renderer.applySettings(settings)
            }

            is Pipeline.Audio -> pipeline.sampler.applySettings(settings)

            is Pipeline.Video -> pipeline.renderer.applySettings(settings)
        }
    }

    /**
     * Renderer
     */

    private val _renderer = MutableStateFlow<Renderer?>(null)

    override val renderer = _renderer.asStateFlow()

    /**
     * State
     */

    private var internalState: InternalPlayerState = InternalPlayerState.Empty

    override val state = MutableStateFlow<PlayerState>(PlayerState.Empty)

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

    override val bufferTimestamp = MutableStateFlow(Timestamp.ZERO)

    override val playbackTimestamp = MutableStateFlow(Timestamp.ZERO)

    private suspend fun handleBufferTimestamp(timestamp: Timestamp) {
        (internalState as? InternalPlayerState.Ready)?.status?.let { status ->
            if (status == InternalPlayerState.Ready.Status.PLAYING || status == InternalPlayerState.Ready.Status.PAUSED) {
                bufferTimestamp.emit(
                    timestamp.copy(
                        micros = timestamp.micros.coerceAtLeast(0L),
                        millis = timestamp.millis.coerceAtLeast(0L)
                    )
                )
            }
        }
    }

    private suspend fun handlePlaybackTimestamp(timestamp: Timestamp) {
        (internalState as? InternalPlayerState.Ready)?.status?.let { status ->
            if (status == InternalPlayerState.Ready.Status.PLAYING) {
                playbackTimestamp.emit(
                    timestamp.copy(
                        micros = timestamp.micros.coerceAtLeast(0L),
                        millis = timestamp.millis.coerceAtLeast(0L)
                    )
                )
            }
        }
    }

    /**
     * Events
     */

    private suspend fun handleException(throwable: Throwable) {
        events.emit(PlayerEvent.Error((throwable as? Exception) ?: Exception(throwable)))

        handleRelease()
    }

    private suspend fun handleBufferWaiting() {
        events.emit(PlayerEvent.Buffer.Waiting)
    }

    /**
     * Completion
     */

    private suspend fun InternalPlayerState.Ready.handleBufferCompletion() {
        bufferLoop.stop().getOrThrow()

        bufferTimestamp.emit(Timestamp(micros = media.durationMicros))

        events.emit(PlayerEvent.Buffer.Complete)
    }

    private suspend fun InternalPlayerState.Ready.handlePlaybackCompletion() {
        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Video -> Unit
        }

        playbackTimestamp.emit(Timestamp(micros = media.durationMicros))

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.COMPLETED))
    }

    /**
     * Command
     */

    private val executionMutex = Mutex()

    private val executionScope = CoroutineScope(Dispatchers.Default)

    private var executionJob: Job? = null

    private suspend fun executeMediaCommand(
        command: suspend () -> Unit,
    ) = executionMutex.withLock {
        command()
    }

    private suspend fun executePlaybackCommand(
        command: suspend InternalPlayerState.Ready.() -> Unit,
    ) = executionMutex.withLock {
        check(internalState is InternalPlayerState.Ready) { "Unable to execute playback command" }

        when (val currentInternalState = internalState) {
            is InternalPlayerState.Ready -> if (currentInternalState.media.durationMicros > 0L) {
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
        hardwareAccelerationCandidates: List<HardwareAcceleration>?,
        loadPreview: Boolean,
    ) = executeMediaCommand {
        updateState(InternalPlayerState.Preparing)

        val media = Decoder.probe(
            location = location,
            findAudioStream = audioBufferSize >= MIN_AUDIO_BUFFER_SIZE,
            findVideoStream = videoBufferSize >= MIN_VIDEO_BUFFER_SIZE
        ).getOrThrow()

        val pipeline = when (media) {
            is Media.Audio -> with(media) {
                val decoder = audioDecoderFactory.create(
                    parameters = AudioDecoderFactory.Parameters(
                        location = location,
                        sampleRate = sampleRate,
                        channels = channels
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

                val renderer = rendererFactory.create(
                    parameters = RendererFactory.Parameters(
                        format = format,
                        preview = if (loadPreview) {
                            decoder.decode().onSuccess {
                                decoder.reset().getOrThrow()
                            }.getOrNull() as? Frame.Video.Content
                        } else null
                    )
                ).getOrThrow()

                _renderer.emit(renderer)

                Pipeline.Video(media = media, decoder = decoder, buffer = buffer, renderer = renderer)
            }

            is Media.AudioVideo -> with(media) {
                val audioDecoder = audioDecoderFactory.create(
                    parameters = AudioDecoderFactory.Parameters(
                        location = location,
                        sampleRate = sampleRate,
                        channels = channels
                    )
                ).getOrThrow()

                val audioBuffer = audioBufferFactory.create(
                    parameters = AudioBufferFactory.Parameters(capacity = audioBufferSize)
                ).getOrThrow()

                val sampler = samplerFactory.create(
                    parameters = SamplerFactory.Parameters(
                        sampleRate = audioFormat.sampleRate,
                        channels = audioFormat.channels
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

                val renderer = rendererFactory.create(
                    parameters = RendererFactory.Parameters(
                        format = videoFormat,
                        preview = if (loadPreview) {
                            videoDecoder.decode().onSuccess {
                                videoDecoder.reset().getOrThrow()
                            }.getOrNull() as? Frame.Video.Content
                        } else null
                    )
                ).getOrThrow()

                _renderer.emit(renderer)

                Pipeline.AudioVideo(
                    media = media,
                    audioDecoder = audioDecoder,
                    videoDecoder = videoDecoder,
                    audioBuffer = audioBuffer,
                    videoBuffer = videoBuffer,
                    sampler = sampler,
                    renderer = renderer
                )
            }
        }

        applySettings(pipeline, settings.value).getOrThrow()

        val bufferLoop = bufferLoopFactory.create(
            parameters = BufferLoopFactory.Parameters(pipeline = pipeline)
        ).getOrThrow()

        val playbackLoop = playbackLoopFactory.create(
            parameters = PlaybackLoopFactory.Parameters(bufferLoop = bufferLoop, pipeline = pipeline)
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

    private suspend fun handlePlay() = executePlaybackCommand {
        updateState(updateStatus(status = InternalPlayerState.Ready.Status.TRANSITION))

        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Video -> Unit
        }

        playbackLoop.start(
            onException = ::handleException,
            onTimestamp = ::handlePlaybackTimestamp,
            endOfMedia = { handlePlaybackCompletion() }
        ).getOrThrow()

        bufferLoop.start(
            onException = ::handleException,
            onTimestamp = ::handleBufferTimestamp,
            onWaiting = ::handleBufferWaiting,
            endOfMedia = { handleBufferCompletion() }
        ).getOrThrow()

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PLAYING))
    }

    private suspend fun handlePause() = executePlaybackCommand {
        updateState(updateStatus(status = InternalPlayerState.Ready.Status.TRANSITION))

        playbackLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> pipeline.sampler.pause().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.pause().getOrThrow()

            is Pipeline.Video -> Unit
        }

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PAUSED))
    }

    private suspend fun handleResume() = executePlaybackCommand {
        updateState(updateStatus(status = InternalPlayerState.Ready.Status.TRANSITION))

        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Video -> Unit
        }

        playbackLoop.start(
            onException = ::handleException,
            onTimestamp = ::handlePlaybackTimestamp,
            endOfMedia = { handlePlaybackCompletion() }
        ).getOrThrow()

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PLAYING))
    }

    private suspend fun handleStop() = executePlaybackCommand {
        updateState(updateStatus(status = InternalPlayerState.Ready.Status.TRANSITION))

        playbackLoop.stop().getOrThrow()

        bufferLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> with(pipeline) {
                sampler.stop().getOrThrow()
                renderer.reset().getOrThrow()
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
                renderer.reset().getOrThrow()
                buffer.flush().getOrThrow()
                decoder.reset().getOrThrow()
            }
        }

        joinAll(
            executionScope.launch {
                bufferTimestamp.emit(Timestamp.ZERO)
            },
            executionScope.launch {
                playbackTimestamp.emit(Timestamp.ZERO)
            }
        )

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.STOPPED))
    }

    private suspend fun handleSeekTo(millis: Long, keyFramesOnly: Boolean) = executePlaybackCommand {
        updateState(updateStatus(status = InternalPlayerState.Ready.Status.SEEKING))

        playbackLoop.stop().getOrThrow()

        bufferLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> with(pipeline) {
                sampler.stop().getOrThrow()

                audioBuffer.flush().getOrThrow()

                videoBuffer.flush().getOrThrow()

                audioDecoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds, keyframesOnly = keyFramesOnly
                ).getOrThrow()

                videoDecoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds, keyframesOnly = keyFramesOnly
                ).getOrThrow()
            }

            is Pipeline.Audio -> with(pipeline) {
                sampler.stop().getOrThrow()

                buffer.flush().getOrThrow()

                decoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds, keyframesOnly = keyFramesOnly
                ).getOrThrow()
            }

            is Pipeline.Video -> with(pipeline) {
                buffer.flush().getOrThrow()

                decoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds, keyframesOnly = keyFramesOnly
                ).getOrThrow()
            }
        }

        bufferLoop.start(
            onException = ::handleException,
            onTimestamp = ::handleBufferTimestamp,
            onWaiting = ::handleBufferWaiting,
            endOfMedia = { handleBufferCompletion() }
        ).getOrThrow()

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PAUSED))
    }

    private suspend fun handleRelease() = executeMediaCommand {
        when (val currentInternalState = internalState) {
            is InternalPlayerState.Ready -> {
                updateState(currentInternalState.updateStatus(status = InternalPlayerState.Ready.Status.RELEASING))

                with(currentInternalState) {
                    playbackLoop.close().getOrThrow()
                    bufferLoop.close().getOrThrow()
                    pipeline.close().getOrThrow()
                }

                joinAll(
                    executionScope.launch {
                        bufferTimestamp.emit(Timestamp.ZERO)
                    },
                    executionScope.launch {
                        playbackTimestamp.emit(Timestamp.ZERO)
                    }
                )

                updateState(InternalPlayerState.Empty)
            }

            else -> return@executeMediaCommand
        }
    }

    override val events = MutableSharedFlow<PlayerEvent>()

    override suspend fun changeSettings(newSettings: PlayerSettings) = executionMutex.withLock {
        runCatching {
            (internalState as? InternalPlayerState.Ready)?.run {
                applySettings(pipeline, newSettings).getOrThrow()
            }

            settings.emit(newSettings)
        }
    }

    override suspend fun resetSettings() = executionMutex.withLock {
        runCatching {
            val newSettings = initialSettings ?: defaultSettings

            settings.emit(newSettings)
        }
    }

    override suspend fun execute(command: Command) = runCatching {
        val status = (internalState as? InternalPlayerState.Ready)?.status

        when (command) {
            is Command.Prepare -> if (internalState is InternalPlayerState.Empty) {
                with(command) {
                    executionScope.launch {
                        handlePrepare(
                            location = location,
                            audioBufferSize = audioBufferSize,
                            videoBufferSize = videoBufferSize,
                            sampleRate = sampleRate,
                            channels = channels,
                            width = width,
                            height = height,
                            frameRate = frameRate,
                            hardwareAccelerationCandidates = hardwareAccelerationCandidates,
                            loadPreview = loadPreview
                        )
                    }.also { job ->
                        executionJob = job
                    }.join()
                }
            }

            is Command.Play -> if (status == InternalPlayerState.Ready.Status.STOPPED) {
                executionScope.launch {
                    handlePlay()
                }.also { job ->
                    executionJob = job
                }.join()
            }

            is Command.Pause -> if (status == InternalPlayerState.Ready.Status.PLAYING) {
                executionScope.launch {
                    handlePause()
                }.also { job ->
                    executionJob = job
                }.join()
            }

            is Command.Resume -> if (status == InternalPlayerState.Ready.Status.PAUSED) {
                executionScope.launch {
                    handleResume()
                }.also { job ->
                    executionJob = job
                }.join()
            }

            is Command.Stop -> if (status == InternalPlayerState.Ready.Status.PLAYING || status == InternalPlayerState.Ready.Status.PAUSED || status == InternalPlayerState.Ready.Status.COMPLETED || status == InternalPlayerState.Ready.Status.SEEKING) {
                if (status == InternalPlayerState.Ready.Status.SEEKING) {
                    executionJob?.cancelAndJoin()
                }

                executionScope.launch {
                    handleStop()
                }.also { job ->
                    executionJob = job
                }.join()
            }

            is Command.SeekTo -> if (status == InternalPlayerState.Ready.Status.PLAYING || status == InternalPlayerState.Ready.Status.PAUSED || status == InternalPlayerState.Ready.Status.STOPPED || status == InternalPlayerState.Ready.Status.COMPLETED || status == InternalPlayerState.Ready.Status.SEEKING) {
                if (status == InternalPlayerState.Ready.Status.SEEKING) {
                    executionJob?.cancelAndJoin()
                }

                executionScope.launch {
                    handleSeekTo(millis = command.millis, keyFramesOnly = command.keyFramesOnly)
                }.also { job ->
                    executionJob = job
                }.join()
            }

            is Command.Release -> if (internalState is InternalPlayerState.Preparing || internalState is InternalPlayerState.Ready) {
                executionJob?.cancel()

                executionJob = null

                handleRelease()
            }
        }
    }

    override suspend fun close() = executionMutex.withLock {
        runCatching {
            executionScope.cancel()

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