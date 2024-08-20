package controller

import buffer.Buffer
import buffer.BufferFactory
import command.Command
import decoder.Decoder
import decoder.DecoderFactory
import event.Event
import factory.Factory
import frame.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
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

    private val mutex = Mutex()

    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

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

    @Volatile
    private var internalState: InternalState = InternalState.Empty

    override val state = MutableStateFlow<State>(State.Empty)

    private suspend fun updateState(newState: InternalState) {
        internalState = newState

        val updatedRenderer = when (val pipeline = (internalState as? InternalState.Loaded)?.pipeline) {
            null -> null

            is Pipeline.Media -> pipeline.renderer

            is Pipeline.Audio -> null

            is Pipeline.Video -> pipeline.renderer
        }

        renderer.emit(updatedRenderer)

        val updatedState = when (val updatedInternalState = internalState) {
            is InternalState.Empty -> State.Empty

            is InternalState.Loaded.Playing -> State.Loaded.Playing(media = updatedInternalState.media)

            is InternalState.Loaded.Paused -> State.Loaded.Paused(media = updatedInternalState.media)

            is InternalState.Loaded.Stopped -> State.Loaded.Stopped(media = updatedInternalState.media)

            is InternalState.Loaded.Completed -> State.Loaded.Completed(media = updatedInternalState.media)

            is InternalState.Loaded.Seeking -> State.Loaded.Seeking(media = updatedInternalState.media)
        }

        state.emit(updatedState)
    }

    /**
     * Timestamp
     */

    override val bufferTimestamp = MutableStateFlow(Timestamp.ZERO)

    override val playbackTimestamp = MutableStateFlow(Timestamp.ZERO)

    private suspend fun handleBufferTimestamp(timestamp: Timestamp) {
        bufferTimestamp.emit(timestamp)
    }

    private suspend fun handlePlaybackTimestamp(timestamp: Timestamp) {
        playbackTimestamp.emit(timestamp)
    }

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

    /**
     * Completion
     */

    private suspend fun InternalState.Loaded.handleBufferCompletion() {
        bufferLoop.stop().getOrThrow()

        bufferTimestamp.emit(Timestamp(micros = media.durationMicros))

        playbackEvents.emit(Event.Buffer.Complete)
    }

    private suspend fun InternalState.Loaded.handlePlaybackCompletion() {
        playbackLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.Media -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Video -> Unit
        }

        playbackTimestamp.emit(Timestamp(micros = media.durationMicros))

        updateState(
            InternalState.Loaded.Completed(
                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )
    }

    /**
     * Command
     */

    private suspend fun executeMediaCommand(
        command: suspend () -> Unit,
    ) = mutex.withLock {
        runCatching {
            command()
        }.onFailure { t -> handleMediaError(t) }.getOrDefault(Unit)
    }

    private suspend fun executePlaybackCommand(
        command: suspend InternalState.Loaded.() -> Unit,
    ) = mutex.withLock {
        runCatching {
            command(internalState as InternalState.Loaded)
        }.onFailure { t -> handlePlaybackError(t) }.getOrDefault(Unit)
    }

    private suspend fun handlePrepare(
        location: String,
        audioBufferSize: Int,
        videoBufferSize: Int,
    ) = executeMediaCommand {
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

        updateState(
            InternalState.Loaded.Stopped(
                media = probeDecoder.media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )
    }

    private suspend fun handlePlay() = executePlaybackCommand {
        when (val pipeline = pipeline) {
            is Pipeline.Media -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Video -> Unit
        }

        bufferLoop.start(
            onTimestamp = ::handleBufferTimestamp,
            onWaiting = ::handleBufferWaiting,
            endOfMedia = { handleBufferCompletion() }
        ).getOrThrow()

        playbackLoop.start(
            onTimestamp = ::handlePlaybackTimestamp,
            endOfMedia = { handlePlaybackCompletion() }
        ).getOrThrow()

        updateState(
            InternalState.Loaded.Playing(
                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )
    }

    private suspend fun handlePause() = executePlaybackCommand {
        playbackLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.Media -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.stop().getOrThrow()

            is Pipeline.Video -> Unit
        }

        updateState(
            InternalState.Loaded.Paused(
                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )
    }

    private suspend fun handleResume() = executePlaybackCommand {
        when (val pipeline = pipeline) {
            is Pipeline.Media -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Audio -> pipeline.sampler.start().getOrThrow()

            is Pipeline.Video -> Unit
        }

        playbackLoop.start(
            onTimestamp = ::handlePlaybackTimestamp,
            endOfMedia = { handlePlaybackCompletion() }
        ).getOrThrow()

        playbackEvents.emit(Event.Playback.Resume)

        updateState(
            InternalState.Loaded.Playing(
                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )
    }

    private suspend fun handleStop() = executePlaybackCommand {
        playbackLoop.stop().getOrThrow()

        bufferLoop.stop().getOrThrow()

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

        handleBufferTimestamp(Timestamp.ZERO)

        handlePlaybackTimestamp(Timestamp.ZERO)

        updateState(
            InternalState.Loaded.Stopped(
                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )
    }

    private suspend fun handleSeekTo(millis: Long) = executePlaybackCommand {
        updateState(
            InternalState.Loaded.Seeking(
                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )

        playbackEvents.emit(Event.Seeking.Start)

        playbackLoop.stop().getOrThrow()

        bufferLoop.stop().getOrThrow()

        when (val pipeline = pipeline) {
            is Pipeline.Media -> with(pipeline) {
                sampler.stop().getOrThrow()

                audioDecoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds
                ).getOrThrow()

                videoDecoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds
                ).getOrThrow()
            }

            is Pipeline.Audio -> with(pipeline) {
                sampler.stop().getOrThrow()

                decoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds
                ).getOrThrow()
            }

            is Pipeline.Video -> with(pipeline) {
                decoder.seekTo(
                    micros = millis.milliseconds.inWholeMicroseconds
                ).getOrThrow()
            }
        }

        bufferLoop.start(onTimestamp = ::handleBufferTimestamp,
            onWaiting = ::handleBufferWaiting,
            endOfMedia = { handleBufferCompletion() }).getOrThrow()

        playbackEvents.emit(Event.Seeking.Complete)

        updateState(
            InternalState.Loaded.Paused(
                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )
    }

    private suspend fun handleRelease() = executeMediaCommand {
        val currentInternalState = internalState
        if (currentInternalState is InternalState.Loaded) {
            currentInternalState.playbackLoop.close()

            currentInternalState.bufferLoop.close()

            currentInternalState.pipeline.close()

            handleBufferTimestamp(Timestamp.ZERO)

            handlePlaybackTimestamp(Timestamp.ZERO)

            updateState(InternalState.Empty)
        }
    }

    override val events = merge(mediaEvents,
        playbackEvents.filter { internalState is InternalState.Loaded.Playing }).shareIn(
        scope = coroutineScope,
        started = SharingStarted.Lazily
    )

    override suspend fun changeSettings(newSettings: Settings) = runCatching {
        (internalState as? InternalState.Loaded)?.run {
            applySettings(pipeline, newSettings).getOrThrow()
        }

        settings.emit(newSettings)
    }.onFailure { t -> handleMediaError(t) }.getOrDefault(Unit)

    override suspend fun resetSettings() = runCatching {
        val newSettings = initialSettings ?: defaultSettings

        settings.emit(newSettings)
    }.onFailure { t -> handleMediaError(t) }.getOrDefault(Unit)

    override suspend fun execute(command: Command) {
        when (command) {
            is Command.Prepare -> if (state.value is State.Empty) {
                with(command) {
                    handlePrepare(
                        location = location, audioBufferSize = audioBufferSize, videoBufferSize = videoBufferSize
                    )
                }
            }

            is Command.Play -> if (state.value is State.Loaded.Stopped) {
                handlePlay()
            }

            is Command.Pause -> if (state.value is State.Loaded.Playing) {
                handlePause()
            }

            is Command.Resume -> if (state.value is State.Loaded.Paused) {
                handleResume()
            }

            is Command.Stop -> if (state.value is State.Loaded.Playing || state.value is State.Loaded.Paused || state.value is State.Loaded.Completed || state.value is State.Loaded.Seeking) {
                handleStop()
            }

            is Command.SeekTo -> if (state.value is State.Loaded.Playing || state.value is State.Loaded.Paused || state.value is State.Loaded.Stopped || state.value is State.Loaded.Completed) {
                handleSeekTo(millis = command.millis)
            }

            is Command.Release -> if (state.value is State.Loaded) {
                handleRelease()
            }
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