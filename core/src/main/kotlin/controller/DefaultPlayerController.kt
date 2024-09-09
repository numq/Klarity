package controller

import buffer.AudioBufferFactory
import buffer.Buffer
import buffer.VideoBufferFactory
import command.Command
import decoder.AudioDecoderFactory
import decoder.Decoder
import decoder.ProbeDecoderFactory
import decoder.VideoDecoderFactory
import event.PlayerEvent
import factory.Factory
import factory.SuspendFactory
import frame.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import loop.buffer.BufferLoop
import loop.buffer.BufferLoopFactory
import loop.playback.PlaybackLoop
import loop.playback.PlaybackLoopFactory
import media.Media
import pipeline.Pipeline
import renderer.Renderer
import renderer.RendererFactory
import sampler.Sampler
import sampler.SamplerFactory
import settings.PlayerSettings
import state.InternalPlayerState
import state.PlayerState
import timestamp.Timestamp
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultPlayerController(
    private val initialSettings: PlayerSettings?,
    private val probeDecoderFactory: SuspendFactory<ProbeDecoderFactory.Parameters, Decoder<Media, Frame.Probe>>,
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

    private val playerContext = Dispatchers.Default + SupervisorJob()

    private val playerScope = CoroutineScope(playerContext)

    /**
     * Settings
     */

    private val defaultSettings = PlayerSettings(
        playbackSpeedFactor = 1f,
        isMuted = false,
        volume = 1f,
        audioBufferSize = MIN_AUDIO_BUFFER_SIZE,
        videoBufferSize = MIN_VIDEO_BUFFER_SIZE,
        seekOnlyKeyframes = false
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
                if (pipeline.audioBuffer.capacity != settings.audioBufferSize) {
                    pipeline.audioBuffer.resize(newCapacity = settings.audioBufferSize).getOrDefault(Unit)
                }
                if (pipeline.videoBuffer.capacity != settings.videoBufferSize) {
                    pipeline.videoBuffer.resize(newCapacity = settings.videoBufferSize).getOrDefault(Unit)
                }
                pipeline.sampler.applySettings(settings)
                pipeline.renderer.applySettings(settings)
            }

            is Pipeline.Audio -> {
                if (pipeline.buffer.capacity != settings.audioBufferSize) {
                    pipeline.buffer.resize(newCapacity = settings.audioBufferSize).getOrDefault(Unit)
                }
                pipeline.sampler.applySettings(settings)
            }

            is Pipeline.Video -> {
                if (pipeline.buffer.capacity != settings.videoBufferSize) {
                    pipeline.buffer.resize(newCapacity = settings.videoBufferSize).getOrDefault(Unit)
                }
                pipeline.renderer.applySettings(settings)
            }
        }
    }

    /**
     * Renderer
     */

    override val renderer = MutableStateFlow<Renderer?>(null)

    /**
     * State
     */

    private val internalState = MutableStateFlow<InternalPlayerState>(InternalPlayerState.Empty)

    override val state = MutableStateFlow<PlayerState>(PlayerState.Empty)

    init {
        internalState.buffer().onEach { updatedInternalState ->
            when (updatedInternalState) {
                is InternalPlayerState.Empty -> state.emit(PlayerState.Empty)

                is InternalPlayerState.Preparing -> Unit

                is InternalPlayerState.Ready -> when (updatedInternalState.status) {
                    InternalPlayerState.Ready.Status.TRANSITION -> Unit

                    InternalPlayerState.Ready.Status.PLAYING -> state.emit(PlayerState.Ready.Playing(media = updatedInternalState.media))

                    InternalPlayerState.Ready.Status.PAUSED -> state.emit(PlayerState.Ready.Paused(media = updatedInternalState.media))

                    InternalPlayerState.Ready.Status.STOPPED -> state.emit(PlayerState.Ready.Stopped(media = updatedInternalState.media))

                    InternalPlayerState.Ready.Status.COMPLETED -> state.emit(PlayerState.Ready.Completed(media = updatedInternalState.media))

                    InternalPlayerState.Ready.Status.SEEKING -> state.emit(PlayerState.Ready.Seeking(media = updatedInternalState.media))

                    InternalPlayerState.Ready.Status.RELEASING -> Unit
                }
            }
        }.launchIn(playerScope)
    }

    private suspend fun updateState(newState: InternalPlayerState) {
        val currentInternalState = internalState.value

        if (currentInternalState == newState) return

        val updatedRenderer = when (val pipeline = (newState as? InternalPlayerState.Ready)?.pipeline) {
            null -> null

            is Pipeline.AudioVideo -> pipeline.renderer

            is Pipeline.Audio -> null

            is Pipeline.Video -> pipeline.renderer
        }

        renderer.emit(updatedRenderer)

        internalState.emit(newState)
    }

    /**
     * Timestamp
     */

    override val bufferTimestamp = MutableStateFlow(Timestamp.ZERO)

    override val playbackTimestamp = MutableStateFlow(Timestamp.ZERO)

    private suspend fun handleBufferTimestamp(timestamp: Timestamp) {
        (internalState.value as? InternalPlayerState.Ready)?.status?.let { status ->
            if (status == InternalPlayerState.Ready.Status.PLAYING || status == InternalPlayerState.Ready.Status.PAUSED) {
                bufferTimestamp.emit(timestamp)
            }
        }
    }

    private suspend fun handlePlaybackTimestamp(timestamp: Timestamp) {
        (internalState.value as? InternalPlayerState.Ready)?.status?.let { status ->
            if (status == InternalPlayerState.Ready.Status.PLAYING) {
                playbackTimestamp.emit(timestamp)
            }
        }
    }

    /**
     * Events
     */

    private suspend fun handleMediaError(throwable: Throwable) {
        events.emit(PlayerEvent.Error((throwable as? Exception) ?: Exception(throwable)))
    }

    private suspend fun handlePlaybackError(throwable: Throwable) {
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

    private val executionContext = Dispatchers.Default + Job()

    private val executionScope = CoroutineScope(executionContext)

    private var executionJob = AtomicReference<Job?>()

    private suspend fun executeMediaCommand(
        command: suspend () -> Unit,
    ) = executionMutex.withLock {
        runCatching { command() }.onFailure { t -> handleMediaError(t) }.getOrDefault(Unit)
    }

    private suspend fun executePlaybackCommand(
        command: suspend InternalPlayerState.Ready.() -> Unit,
    ) = executionMutex.withLock {
        runCatching {
            val currentInternalState = internalState.value

            check(currentInternalState is InternalPlayerState.Ready) { "Unable to execute playback command" }

            command(currentInternalState)
        }.onFailure { t ->
            handlePlaybackError(t)
        }.getOrDefault(Unit)
    }

    private suspend fun handlePrepare(
        location: String,
        audioBufferSize: Int,
        videoBufferSize: Int,
    ) = executeMediaCommand {
        updateState(InternalPlayerState.Preparing)

        val media = probeDecoderFactory.create(
            ProbeDecoderFactory.Parameters(
                location = location,
                findAudioStream = audioBufferSize >= MIN_AUDIO_BUFFER_SIZE,
                findVideoStream = videoBufferSize >= MIN_VIDEO_BUFFER_SIZE
            )
        ).getOrThrow().use(Decoder<Media, Frame.Probe>::media)

        val audioPipeline = when (media) {
            is Media.AudioVideo -> media.audioFormat

            is Media.Audio -> media.format

            is Media.Video -> null
        }?.run {
            val decoder = audioDecoderFactory.create(
                parameters = AudioDecoderFactory.Parameters(location = location)
            ).getOrThrow()

            val buffer = audioBufferFactory.create(
                parameters = AudioBufferFactory.Parameters(capacity = audioBufferSize)
            ).getOrThrow()

            val sampler = samplerFactory.create(
                parameters = SamplerFactory.Parameters(sampleRate = sampleRate, channels = channels)
            ).getOrThrow()

            Pipeline.Audio(media = media, decoder = decoder, buffer = buffer, sampler = sampler)
        }

        val videoPipeline = when (media) {
            is Media.AudioVideo -> media.videoFormat

            is Media.Audio -> null

            is Media.Video -> media.format
        }?.run {
            val decoder = videoDecoderFactory.create(
                parameters = VideoDecoderFactory.Parameters(location = location)
            ).getOrThrow()

            val buffer = videoBufferFactory.create(
                parameters = VideoBufferFactory.Parameters(capacity = videoBufferSize)
            ).getOrThrow()

            val renderer = rendererFactory.create(
                parameters = RendererFactory.Parameters(
                    width = width,
                    height = height,
                    frameRate = frameRate,
                    preview = with(decoder) {
                        val frame = nextFrame(null, null).getOrNull() as? Frame.Video.Content

                        reset()

                        frame
                    })
            ).getOrThrow()

            Pipeline.Video(media = media, decoder = decoder, buffer = buffer, renderer = renderer)
        }

        val pipeline = when {
            audioPipeline != null && videoPipeline != null -> Pipeline.AudioVideo(
                media = media,
                audioDecoder = audioPipeline.decoder,
                videoDecoder = videoPipeline.decoder,
                audioBuffer = audioPipeline.buffer,
                videoBuffer = videoPipeline.buffer,
                sampler = audioPipeline.sampler,
                renderer = videoPipeline.renderer
            )

            audioPipeline != null -> audioPipeline

            videoPipeline != null -> videoPipeline

            else -> null
        }

        checkNotNull(pipeline) { "Unsupported media" }

        applySettings(pipeline, settings.value)

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

        bufferLoop.start(onTimestamp = ::handleBufferTimestamp,
            onWaiting = ::handleBufferWaiting,
            endOfMedia = { handleBufferCompletion() }).getOrThrow()

        playbackLoop.start(onTimestamp = ::handlePlaybackTimestamp, endOfMedia = { handlePlaybackCompletion() })
            .getOrThrow()

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PLAYING))
    }

    private suspend fun handlePause() = executePlaybackCommand {
        updateState(updateStatus(status = InternalPlayerState.Ready.Status.TRANSITION))

        playbackLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.stop().getOrThrow()

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

        playbackLoop.start(onTimestamp = ::handlePlaybackTimestamp, endOfMedia = { handlePlaybackCompletion() })
            .getOrThrow()

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
                audioDecoder.reset().getOrThrow()
                videoDecoder.reset().getOrThrow()
            }

            is Pipeline.Audio -> with(pipeline) {
                sampler.stop().getOrThrow()
                decoder.reset().getOrThrow()
            }

            is Pipeline.Video -> with(pipeline) {
                renderer.reset().getOrThrow()
                decoder.reset().getOrThrow()
            }
        }

        bufferTimestamp.emit(Timestamp.ZERO)

        playbackTimestamp.emit(Timestamp.ZERO)

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.STOPPED))
    }

    private suspend fun handleSeekTo(millis: Long) = executePlaybackCommand {
        updateState(updateStatus(status = InternalPlayerState.Ready.Status.SEEKING))

        playbackLoop.stop().getOrThrow()

        bufferLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.AudioVideo -> with(pipeline) {
                sampler.stop().getOrThrow()

                currentCoroutineContext().ensureActive()

                audioBuffer.flush().getOrThrow()

                videoBuffer.flush().getOrThrow()

                currentCoroutineContext().ensureActive()

                audioDecoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds, keyframesOnly = false
                ).getOrThrow()

                videoDecoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds, keyframesOnly = false
                ).getOrThrow()
            }

            is Pipeline.Audio -> with(pipeline) {
                sampler.stop().getOrThrow()

                currentCoroutineContext().ensureActive()

                buffer.flush().getOrThrow()

                currentCoroutineContext().ensureActive()

                decoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds, keyframesOnly = false
                ).getOrThrow()
            }

            is Pipeline.Video -> with(pipeline) {
                currentCoroutineContext().ensureActive()

                buffer.flush().getOrThrow()

                currentCoroutineContext().ensureActive()

                decoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds, keyframesOnly = false
                ).getOrThrow()
            }
        }

        currentCoroutineContext().ensureActive()

        bufferLoop.start(onTimestamp = ::handleBufferTimestamp,
            onWaiting = ::handleBufferWaiting,
            endOfMedia = { handleBufferCompletion() }).getOrThrow()

        currentCoroutineContext().ensureActive()

        updateState(updateStatus(status = InternalPlayerState.Ready.Status.PAUSED))
    }

    private suspend fun handleRelease() = executeMediaCommand {
        val currentInternalState = internalState.value

        if (currentInternalState is InternalPlayerState.Ready) {
            updateState(currentInternalState.updateStatus(status = InternalPlayerState.Ready.Status.RELEASING))

            with(currentInternalState) {
                bufferLoop.close()
                playbackLoop.close()
                pipeline.close()
            }

            bufferTimestamp.emit(Timestamp.ZERO)

            playbackTimestamp.emit(Timestamp.ZERO)

            updateState(InternalPlayerState.Empty)
        }
    }

    override val events = MutableSharedFlow<PlayerEvent>()

    override suspend fun changeSettings(newSettings: PlayerSettings) = runCatching {
        (internalState.value as? InternalPlayerState.Ready)?.run {
            applySettings(pipeline, newSettings).getOrThrow()
        }

        settings.emit(newSettings)
    }.onFailure { t -> handleMediaError(t) }.getOrDefault(Unit)

    override suspend fun resetSettings() = runCatching {
        val newSettings = initialSettings ?: defaultSettings

        settings.emit(newSettings)
    }.onFailure { t -> handleMediaError(t) }.getOrDefault(Unit)

    override suspend fun execute(command: Command) {
        val currentInternalState = internalState.value

        val status = (currentInternalState as? InternalPlayerState.Ready)?.status

        when (command) {
            is Command.Prepare -> if (currentInternalState is InternalPlayerState.Empty) {
                with(command) {
                    executionScope.launch {
                        handlePrepare(
                            location = location,
                            audioBufferSize = audioBufferSize,
                            videoBufferSize = videoBufferSize
                        )
                    }.also(executionJob::set).join()
                }
            }

            is Command.Play -> if (status == InternalPlayerState.Ready.Status.STOPPED) {
                executionScope.launch {
                    handlePlay()
                }.also(executionJob::set).join()
            }

            is Command.Pause -> if (status == InternalPlayerState.Ready.Status.PLAYING) {
                executionScope.launch {
                    handlePause()
                }.also(executionJob::set).join()
            }

            is Command.Resume -> if (status == InternalPlayerState.Ready.Status.PAUSED) {
                executionScope.launch {
                    handleResume()
                }.also(executionJob::set).join()
            }

            is Command.Stop -> if (status == InternalPlayerState.Ready.Status.PLAYING || status == InternalPlayerState.Ready.Status.PAUSED || status == InternalPlayerState.Ready.Status.COMPLETED || status == InternalPlayerState.Ready.Status.SEEKING) {
                if (status == InternalPlayerState.Ready.Status.SEEKING) {
                    executionJob.get()?.cancelAndJoin()
                }
                executionScope.launch {
                    handleStop()
                }.also(executionJob::set).join()
            }

            is Command.SeekTo -> if (status == InternalPlayerState.Ready.Status.PLAYING || status == InternalPlayerState.Ready.Status.PAUSED || status == InternalPlayerState.Ready.Status.STOPPED || status == InternalPlayerState.Ready.Status.COMPLETED || status == InternalPlayerState.Ready.Status.SEEKING) {
                if (status == InternalPlayerState.Ready.Status.SEEKING) {
                    executionJob.get()?.cancelAndJoin()
                }
                executionScope.launch {
                    handleSeekTo(millis = command.millis)
                }.also(executionJob::set).join()
            }

            is Command.Release -> if (currentInternalState is InternalPlayerState.Preparing || currentInternalState is InternalPlayerState.Ready) {
                executionJob.get()?.cancelAndJoin()
                executionJob.set(null)
                handleRelease()
            }
        }
    }

    override fun close() {
        executionScope.coroutineContext.cancelChildren()
        playerScope.coroutineContext.cancelChildren()
        when (val currentState = internalState.value) {
            is InternalPlayerState.Empty,
            is InternalPlayerState.Preparing,
            -> Unit

            is InternalPlayerState.Ready -> with(currentState) {
                bufferLoop.close()
                playbackLoop.close()
                pipeline.close()
            }
        }
    }
}