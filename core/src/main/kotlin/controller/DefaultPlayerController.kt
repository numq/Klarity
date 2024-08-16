package controller

import buffer.Buffer
import buffer.BufferFactory
import command.Command
import decoder.Decoder
import decoder.DecoderFactory
import event.Event
import factory.Factory
import frame.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import loop.buffer.BufferLoop
import loop.buffer.BufferLoopFactory
import loop.playback.PlaybackLoop
import loop.playback.PlaybackLoopFactory
import pipeline.Pipeline
import renderer.Renderer
import renderer.RendererFactory
import sampler.Sampler
import sampler.SamplerFactory
import settings.Settings
import state.InternalState
import state.State
import timestamp.Timestamp
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultPlayerController(
    private val initialSettings: Settings?,
    private val probeDecoderFactory: Factory<DecoderFactory.Parameters, Decoder<Nothing>>,
    private val audioDecoderFactory: Factory<DecoderFactory.Parameters, Decoder<Frame.Audio>>,
    private val videoDecoderFactory: Factory<DecoderFactory.Parameters, Decoder<Frame.Video>>,
    private val audioBufferFactory: Factory<BufferFactory.Parameters, Buffer<Frame.Audio>>,
    private val videoBufferFactory: Factory<BufferFactory.Parameters, Buffer<Frame.Video>>,
    private val bufferLoopFactory: Factory<BufferLoopFactory.Parameters, BufferLoop>,
    private val playbackLoopFactory: Factory<PlaybackLoopFactory.Parameters, PlaybackLoop>,
    private val samplerFactory: Factory<SamplerFactory.Parameters, Sampler>,
    private val rendererFactory: Factory<RendererFactory.Parameters, Renderer>,
) : PlayerController {
    companion object {
        const val MIN_AUDIO_BUFFER_SIZE = 1
        const val MIN_VIDEO_BUFFER_SIZE = 1
    }

    private val stateMutex = Mutex()

    private val commandMutex = Mutex()

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    @Volatile
    private var bufferTimestampJob: Job? = null

    @Volatile
    private var playbackTimestampJob: Job? = null

    /**
     * Settings
     */

    private val defaultSettings = Settings(
        playbackSpeedFactor = 1.0f,
        isMuted = false,
        volume = 1.0f,
        audioBufferSize = MIN_AUDIO_BUFFER_SIZE,
        videoBufferSize = MIN_VIDEO_BUFFER_SIZE,
    )

    override val settings = MutableStateFlow(initialSettings ?: defaultSettings)

    private suspend fun applySettings(pipeline: Pipeline, settings: Settings) = runCatching {
        when (pipeline) {
            is Pipeline.Media -> {
                pipeline.sampler.setVolume(value = settings.volume).getOrDefault(Unit)
                pipeline.sampler.setMuted(state = settings.isMuted).getOrDefault(Unit)
                pipeline.sampler.setPlaybackSpeed(factor = settings.playbackSpeedFactor).getOrDefault(Unit)
                pipeline.renderer.setPlaybackSpeed(factor = settings.playbackSpeedFactor).getOrDefault(Unit)
            }

            is Pipeline.Audio -> {
                pipeline.sampler.setVolume(value = settings.volume).getOrDefault(Unit)
                pipeline.sampler.setMuted(state = settings.isMuted).getOrDefault(Unit)
                pipeline.sampler.setPlaybackSpeed(factor = settings.playbackSpeedFactor).getOrDefault(Unit)
            }

            is Pipeline.Video -> {
                pipeline.renderer.setPlaybackSpeed(factor = settings.playbackSpeedFactor).getOrDefault(Unit)
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

    private var internalState: InternalState = InternalState.Empty

    override val state = MutableStateFlow<State>(State.Empty)

    private suspend fun updateState(newState: InternalState) = stateMutex.withLock {
        internalState = newState

        val updatedState = when (val updatedInternalState = internalState) {
            is InternalState.Empty -> State.Empty

            is InternalState.Loaded.Playing -> State.Loaded.Playing(media = updatedInternalState.media)

            is InternalState.Loaded.Paused -> State.Loaded.Paused(media = updatedInternalState.media)

            is InternalState.Loaded.Stopped -> State.Loaded.Stopped(media = updatedInternalState.media)

            is InternalState.Loaded.Completed -> State.Loaded.Completed(media = updatedInternalState.media)

            is InternalState.Loaded.Seeking -> State.Loaded.Seeking(media = updatedInternalState.media)
        }

        state.emit(updatedState)

        val updatedRenderer = when (val pipeline = (internalState as? InternalState.Loaded)?.pipeline) {
            null -> null

            is Pipeline.Media -> pipeline.renderer

            is Pipeline.Audio -> null

            is Pipeline.Video -> pipeline.renderer
        }

        renderer.emit(updatedRenderer)

        state.first {
            when (newState) {
                is InternalState.Empty -> it is State.Empty

                is InternalState.Loaded.Playing -> it is State.Loaded.Playing

                is InternalState.Loaded.Paused -> it is State.Loaded.Paused

                is InternalState.Loaded.Stopped -> it is State.Loaded.Stopped

                is InternalState.Loaded.Completed -> it is State.Loaded.Completed

                is InternalState.Loaded.Seeking -> it is State.Loaded.Seeking
            }
        }

        renderer.first { it == updatedRenderer }
    }

    private suspend fun InternalState.Loaded.handlePlaybackCompletion() {
        updateState(
            InternalState.Loaded.Completed(
                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )
    }

    /**
     * Timestamp
     */

    override val bufferTimestamp = MutableStateFlow(Timestamp.ZERO)

    override val playbackTimestamp = MutableStateFlow(Timestamp.ZERO)

    /**
     * Events
     */

    private val mediaEvents = MutableSharedFlow<Event>()

    private val playbackEvents = MutableSharedFlow<Event>()

    private suspend fun handleMediaError(throwable: Throwable) {
        mediaEvents.emit(Event.Error((throwable as? Exception) ?: Exception(throwable)))
    }

    private suspend fun handlePlaybackError(throwable: Throwable) {
        playbackEvents.emit(Event.Error((throwable as? Exception) ?: Exception(throwable)))
    }

    private suspend fun handleBufferWaiting() {
        playbackEvents.emit(Event.Buffer.Waiting)
    }

    private suspend fun handleBufferCompletion() {
        playbackEvents.emit(Event.Buffer.Complete)
    }

    /**
     * Command
     */

    private suspend fun executePlaybackCommand(
        onInternalState: suspend InternalState.Loaded.() -> Unit,
    ) = commandMutex.withLock {
        runCatching {
            check(internalState is InternalState.Loaded) { "Unable to execute playback command" }

            onInternalState(internalState as InternalState.Loaded)
        }.onFailure { t -> handlePlaybackError(t) }.getOrDefault(Unit)
    }

    private suspend fun handlePrepare(
        location: String,
        audioBufferSize: Int,
        videoBufferSize: Int,
    ) = runCatching {
        if (internalState is InternalState.Empty) {
            val probeDecoder = probeDecoderFactory.create(
                DecoderFactory.Parameters.Probe(
                    location = location,
                    findAudioStream = audioBufferSize >= MIN_AUDIO_BUFFER_SIZE,
                    findVideoStream = videoBufferSize >= MIN_VIDEO_BUFFER_SIZE
                )
            ).getOrThrow()

            val audioPipeline = probeDecoder.media.audioFormat?.run {
                val decoder = audioDecoderFactory.create(
                    parameters = DecoderFactory.Parameters.Audio(location = location)
                ).getOrThrow()

                val buffer = audioBufferFactory.create(
                    parameters = BufferFactory.Parameters.Audio(capacity = audioBufferSize)
                ).getOrThrow()

                val sampler = samplerFactory.create(
                    parameters = SamplerFactory.Parameters(sampleRate = sampleRate, channels = channels)
                ).getOrThrow()

                Pipeline.Audio(media = probeDecoder.media, decoder = decoder, buffer = buffer, sampler = sampler)
            }

            val videoPipeline = probeDecoder.media.videoFormat?.run {
                val decoder = videoDecoderFactory.create(
                    parameters = DecoderFactory.Parameters.Video(location = location)
                ).getOrThrow()

                val buffer = videoBufferFactory.create(
                    parameters = BufferFactory.Parameters.Video(capacity = videoBufferSize)
                ).getOrThrow()

                val renderer = rendererFactory.create(
                    parameters = RendererFactory.Parameters(width = width,
                        height = height,
                        frameRate = frameRate,
                        preview = with(decoder) {
                            val frame = nextFrame().getOrNull() as? Frame.Video.Content

                            reset()

                            frame
                        })
                ).getOrThrow()

                Pipeline.Video(media = probeDecoder.media, decoder = decoder, buffer = buffer, renderer = renderer)
            }

            val pipeline = when {
                audioPipeline != null && videoPipeline != null -> Pipeline.Media(
                    media = probeDecoder.media,
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

            bufferTimestampJob = coroutineScope.launch {
                bufferTimestamp.emitAll(bufferLoop.timestamp)
            }

            playbackTimestampJob = coroutineScope.launch {
                playbackTimestamp.emitAll(playbackLoop.timestamp)
            }

            updateState(
                InternalState.Loaded.Stopped(
                    media = probeDecoder.media,
                    pipeline = pipeline,
                    bufferLoop = bufferLoop,
                    playbackLoop = playbackLoop
                )
            )
        }
    }.onFailure { t -> handleMediaError(t) }

    private suspend fun handlePlay() = executePlaybackCommand {
        if (internalState is InternalState.Loaded.Stopped) {
            when (val pipeline = pipeline) {
                is Pipeline.Media -> pipeline.sampler.start().getOrThrow()

                is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

                is Pipeline.Video -> Unit
            }

            bufferLoop.start(
                onWaiting = ::handleBufferWaiting, endOfMedia = ::handleBufferCompletion
            ).getOrThrow()

            playbackLoop.start(endOfMedia = { handlePlaybackCompletion() }).getOrThrow()

            updateState(
                InternalState.Loaded.Playing(
                    media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                )
            )
        }
    }

    private suspend fun handlePause() = executePlaybackCommand {
        if (internalState is InternalState.Loaded.Playing) {
            playbackLoop.stop(resetTime = false).getOrThrow()

            when (val pipeline = pipeline) {
                is Pipeline.Media -> pipeline.sampler.pause().getOrThrow()

                is Pipeline.Audio -> pipeline.sampler.pause().getOrThrow()

                is Pipeline.Video -> Unit
            }

            updateState(
                InternalState.Loaded.Paused(
                    media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                )
            )
        }
    }

    private suspend fun handleResume() = executePlaybackCommand {
        if (internalState is InternalState.Loaded.Paused) {
            when (val pipeline = pipeline) {
                is Pipeline.Media -> pipeline.sampler.resume().getOrThrow()

                is Pipeline.Audio -> pipeline.sampler.resume().getOrThrow()

                is Pipeline.Video -> Unit
            }

            playbackLoop.start(endOfMedia = { handlePlaybackCompletion() }).getOrThrow()

            playbackEvents.emit(Event.Playback.Resume)

            updateState(
                InternalState.Loaded.Playing(
                    media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                )
            )
        }
    }

    private suspend fun handleStop() = executePlaybackCommand {
        if (internalState is InternalState.Loaded.Playing || internalState is InternalState.Loaded.Paused || internalState is InternalState.Loaded.Completed || internalState is InternalState.Loaded.Seeking) {
            playbackLoop.stop(resetTime = true).getOrThrow()

            bufferLoop.stop(resetTime = true).getOrThrow()

            when (val pipeline = pipeline) {
                is Pipeline.Media -> with(pipeline) {
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

            when (val pipeline = pipeline) {
                is Pipeline.Media -> pipeline.renderer.reset().getOrThrow()

                is Pipeline.Audio -> Unit

                is Pipeline.Video -> pipeline.renderer.reset().getOrThrow()
            }

            updateState(
                InternalState.Loaded.Stopped(
                    media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                )
            )
        }
    }

    private suspend fun handleSeekTo(millis: Long) = executePlaybackCommand {
        if (internalState is InternalState.Loaded.Playing || internalState is InternalState.Loaded.Paused || internalState is InternalState.Loaded.Stopped || internalState is InternalState.Loaded.Completed) {
            updateState(
                InternalState.Loaded.Seeking(
                    media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                )
            )

            playbackEvents.emit(Event.Seeking.Start)

            playbackLoop.stop(resetTime = false).getOrThrow()

            bufferLoop.stop(resetTime = false).getOrThrow()

            when (val pipeline = pipeline) {
                is Pipeline.Media -> with(pipeline) {
                    sampler.stop().getOrThrow()

                    audioDecoder.seekTo(
                        micros = millis.milliseconds.inWholeMicroseconds
                    ).getOrThrow()

                    videoDecoder.seekTo(
                        micros = millis.milliseconds.inWholeMicroseconds
                    ).getOrThrow()

                    sampler.start().getOrThrow()

                    sampler.pause().getOrThrow()
                }

                is Pipeline.Audio -> with(pipeline) {
                    sampler.stop().getOrThrow()

                    decoder.seekTo(
                        micros = millis.milliseconds.inWholeMicroseconds
                    ).getOrThrow()

                    sampler.start().getOrThrow()

                    sampler.pause().getOrThrow()
                }

                is Pipeline.Video -> with(pipeline) {
                    decoder.seekTo(
                        micros = millis.milliseconds.inWholeMicroseconds
                    ).getOrThrow()
                }
            }

            bufferLoop.start(onWaiting = ::handleBufferWaiting, endOfMedia = ::handleBufferCompletion).getOrThrow()

            playbackLoop.seekTo(
                timestamp = Timestamp(micros = millis.milliseconds.inWholeMicroseconds)
            ).getOrThrow()

            playbackEvents.emit(Event.Seeking.Complete)

            updateState(
                InternalState.Loaded.Paused(
                    media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                )
            )
        }
    }

    private suspend fun handleRelease() = commandMutex.withLock {
        runCatching {
            val currentInternalState = internalState
            if (currentInternalState is InternalState.Loaded) {
                bufferTimestampJob?.cancelAndJoin()
                bufferTimestampJob = null
                playbackTimestampJob?.cancelAndJoin()
                playbackTimestampJob = null
                currentInternalState.playbackLoop.close()
                currentInternalState.bufferLoop.close()
                currentInternalState.pipeline.close()
                updateState(InternalState.Empty)
            }
        }.onFailure { t -> handleMediaError(t) }
    }

    override val events = merge(mediaEvents,
        playbackEvents.filter { internalState is InternalState.Loaded.Playing }).shareIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily
    )

    override suspend fun changeSettings(newSettings: Settings) = commandMutex.withLock {
        runCatching {
            (internalState as? InternalState.Loaded)?.run {
                applySettings(pipeline, newSettings).getOrThrow()
            }

            settings.emit(newSettings)

            settings.first { it == newSettings }

            Unit
        }.onFailure { t -> handleMediaError(t) }.getOrDefault(Unit)
    }

    override suspend fun resetSettings() = commandMutex.withLock {
        runCatching {
            val newSettings = initialSettings ?: defaultSettings

            settings.emit(newSettings)

            settings.first { it == newSettings }

            Unit
        }.onFailure { t -> handleMediaError(t) }.getOrDefault(Unit)
    }

    override suspend fun execute(command: Command) {
        when (command) {
            is Command.Prepare -> with(command) {
                handlePrepare(
                    location = location,
                    audioBufferSize = audioBufferSize,
                    videoBufferSize = videoBufferSize
                )
            }

            is Command.Play -> handlePlay()
            is Command.Pause -> handlePause()
            is Command.Resume -> handleResume()
            is Command.Stop -> handleStop()
            is Command.SeekTo -> handleSeekTo(millis = command.millis)
            is Command.Release -> handleRelease()
        }
    }

    override fun close() = runCatching {
        coroutineScope.cancel()

        when (val currentInternalState = internalState) {
            is InternalState.Empty -> Unit

            is InternalState.Loaded -> with(currentInternalState) {
                playbackLoop.close()
                bufferLoop.close()
                pipeline.close()
            }
        }
    }.getOrDefault(Unit)
}