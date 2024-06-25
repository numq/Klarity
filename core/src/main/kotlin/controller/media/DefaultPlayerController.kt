package controller.media

import buffer.Buffer
import buffer.BufferFactory
import clock.Clock
import clock.ClockFactory
import command.Command
import decoder.Decoder
import decoder.DecoderFactory
import event.Event
import factory.Factory
import format.AudioFormat
import format.VideoFormat
import frame.Frame
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
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
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultPlayerController(
    private val defaultSettings: Settings?,
    private val clockFactory: Factory<ClockFactory.Parameters, Clock>,
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
    private val coroutineScope = CoroutineScope(Dispatchers.Default)

    private val clock by lazy { clockFactory.create(ClockFactory.Parameters) }

    @Volatile
    private var bufferTimestampJob: Job? = null

    @Volatile
    private var playbackTimestampJob: Job? = null

    override val settings = MutableStateFlow(defaultSettings ?: Settings.DEFAULT_SETTINGS)

    private val commonEvents = MutableSharedFlow<Event>()

    private suspend fun handleCommonError(throwable: Throwable) {
        commonEvents.emit(Event.Error((throwable as? Exception) ?: Exception(throwable)))
    }

    private val loadedEvents = MutableSharedFlow<Event>()

    private suspend fun handleBufferCompletion() {
        loadedEvents.emit(Event.Buffer.Complete)
    }

    private suspend fun handleLoadedError(throwable: Throwable) {
        loadedEvents.emit(Event.Error((throwable as? Exception) ?: Exception(throwable)))
    }

    private val playingEvents = MutableSharedFlow<Event>()

    private suspend fun handleBufferWaiting() {
        playingEvents.emit(Event.Buffer.Waiting)
    }

    private suspend fun handleBufferTimestamp(millis: Long) {
        playingEvents.emit(Event.Buffer.Timestamp(millis = millis))
    }

    private suspend fun handlePlaybackTimestamp(millis: Long) {
        playingEvents.emit(Event.Playback.Timestamp(millis = millis))
    }

    override val events = merge(commonEvents,
        loadedEvents.filter { internalState.value is InternalState.Loaded },
        playingEvents.filter { internalState.value is InternalState.Loaded.Playing }).shareIn(
        scope = coroutineScope,
        started = SharingStarted.Eagerly
    )

    override val internalState = MutableStateFlow<InternalState>(InternalState.Empty)

    private suspend fun updateState(newState: InternalState) {
        coroutineScope.launch {
            internalState.emit(newState)

            internalState.first { state -> state == newState }
        }.join()
    }

    private suspend fun InternalState.Loaded.handlePlaybackCompletion() {
        updateState(
            InternalState.Loaded.Completed(
                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )
    }

    override suspend fun changeSettings(newSettings: Settings) = settings.value.runCatching {
        if (newSettings.volume != volume) {
            (internalState.value as? InternalState.Loaded)?.run {
                when (val pipeline = pipeline) {
                    is Pipeline.Media -> pipeline.sampler.setVolume(value = newSettings.volume).getOrThrow()

                    is Pipeline.Audio -> pipeline.sampler.setVolume(value = newSettings.volume).getOrThrow()

                    else -> null
                }
            }

            settings.value = settings.value.copy(volume = newSettings.volume)
        }

        if (newSettings.isMuted != isMuted) {
            (internalState.value as? InternalState.Loaded)?.run {
                when (val pipeline = pipeline) {
                    is Pipeline.Media -> pipeline.sampler.setMuted(state = newSettings.isMuted).getOrThrow()

                    is Pipeline.Audio -> pipeline.sampler.setMuted(state = newSettings.isMuted).getOrThrow()

                    else -> null
                }
            }

            settings.value = settings.value.copy(isMuted = newSettings.isMuted)
        }

        if (newSettings.audioBufferSize != audioBufferSize) {
            settings.value = settings.value.copy(audioBufferSize = newSettings.audioBufferSize)
        }

        if (newSettings.videoBufferSize != videoBufferSize) {
            settings.value = settings.value.copy(videoBufferSize = newSettings.videoBufferSize)
        }

        if (newSettings.playbackSpeedFactor != playbackSpeedFactor) {
            clock.getOrNull()?.setPlaybackSpeed(newSettings.playbackSpeedFactor.toDouble())?.getOrThrow()

            (internalState.value as? InternalState.Loaded)?.run {
                when (val pipeline = pipeline) {
                    is Pipeline.Media -> {
                        pipeline.sampler.setPlaybackSpeed(factor = newSettings.playbackSpeedFactor).getOrThrow()
                        pipeline.renderer.setPlaybackSpeed(factor = newSettings.playbackSpeedFactor).getOrThrow()
                    }

                    is Pipeline.Audio -> pipeline.sampler.setPlaybackSpeed(factor = newSettings.playbackSpeedFactor)
                        .getOrThrow()

                    is Pipeline.Video -> pipeline.renderer.setPlaybackSpeed(factor = newSettings.playbackSpeedFactor)
                        .getOrThrow()
                }
            }

            settings.value = settings.value.copy(playbackSpeedFactor = newSettings.playbackSpeedFactor)
        }
    }.onFailure { t -> handleCommonError(t) }.getOrDefault(Unit)

    override suspend fun resetSettings() = runCatching {
        settings.value = defaultSettings ?: Settings.DEFAULT_SETTINGS
    }.onFailure { t -> handleCommonError(t) }.getOrDefault(Unit)

    override suspend fun prepare(location: String, audioBufferSize: Int, videoBufferSize: Int) = runCatching {
        check(internalState.value is InternalState.Empty) { "Unable to prepare non-empty controller" }

        val probeDecoder = probeDecoderFactory.create(
            DecoderFactory.Parameters.Probe(
                location = location,
                findAudioStream = audioBufferSize >= AudioFormat.MIN_BUFFER_SIZE,
                findVideoStream = videoBufferSize >= VideoFormat.MIN_BUFFER_SIZE
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
                parameters = SamplerFactory.Parameters(
                    sampleRate = sampleRate, channels = channels
                ),
            ).getOrThrow()

            Pipeline.Audio(decoder = decoder, buffer = buffer, sampler = sampler)
        }

        val videoPipeline = probeDecoder.media.videoFormat?.run {
            val decoder = videoDecoderFactory.create(
                parameters = DecoderFactory.Parameters.Video(location = location)
            ).getOrThrow()

            val buffer = videoBufferFactory.create(
                parameters = BufferFactory.Parameters.Video(capacity = videoBufferSize)
            ).getOrThrow()

            val renderer = rendererFactory.create(
                parameters = RendererFactory.Parameters(width = width, height = height, preview = with(decoder) {
                    val frame = decoder.nextFrame().getOrNull() as? Frame.Video.Content

                    reset()

                    frame
                })
            ).getOrThrow()

            Pipeline.Video(decoder = decoder, buffer = buffer, renderer = renderer)
        }

        val pipeline = when {
            audioPipeline != null && videoPipeline != null -> Pipeline.Media(
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

        with(settings.value) {
            when (pipeline) {
                is Pipeline.Media -> {
                    with(pipeline.sampler) {
                        setMuted(isMuted).getOrThrow()
                        setVolume(volume).getOrThrow()
                        setPlaybackSpeed(playbackSpeedFactor).getOrThrow()
                    }
                    pipeline.renderer.setPlaybackSpeed(playbackSpeedFactor).getOrThrow()
                }

                is Pipeline.Audio -> with(pipeline.sampler) {
                    setMuted(isMuted).getOrThrow()
                    setVolume(volume).getOrThrow()
                    setPlaybackSpeed(playbackSpeedFactor).getOrThrow()
                }

                is Pipeline.Video -> pipeline.renderer.setPlaybackSpeed(playbackSpeedFactor).getOrThrow()
            }
        }

        val bufferLoop = bufferLoopFactory.create(
            parameters = BufferLoopFactory.Parameters(pipeline = pipeline)
        ).getOrThrow()

        val playbackLoop = playbackLoopFactory.create(
            parameters = PlaybackLoopFactory.Parameters(
                clock = clock.getOrThrow(),
                bufferLoop = bufferLoop,
                pipeline = pipeline
            )
        ).getOrThrow()

        bufferTimestampJob = bufferLoop.timestamp.onEach { millis ->
            handleBufferTimestamp(millis)
        }.launchIn(coroutineScope)

        playbackTimestampJob = playbackLoop.timestamp.onEach { millis ->
            handlePlaybackTimestamp(millis)
        }.launchIn(coroutineScope)

        updateState(
            InternalState.Loaded.Stopped(
                media = probeDecoder.media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
            )
        )
    }.onFailure { t -> handleLoadedError(t) }.getOrDefault(Unit)

    override suspend fun execute(command: Command) = runCatching {
        check(internalState.value is InternalState.Loaded) { "Unable to execute playback command" }

        when (val internalState = internalState.value) {
            is InternalState.Empty -> Unit

            is InternalState.Loaded -> with(internalState) {
                when (command) {
                    is Command.Play -> {
                        bufferLoop.start(
                            onWaiting = ::handleBufferWaiting,
                            endOfMedia = ::handleBufferCompletion
                        ).getOrThrow()

                        playbackLoop.start(endOfMedia = { internalState.handlePlaybackCompletion() }).getOrThrow()

                        updateState(
                            InternalState.Loaded.Playing(
                                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                            )
                        )
                    }

                    is Command.Pause -> {
                        playbackLoop.stop().getOrThrow()

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

                    is Command.Resume -> {
                        when (val pipeline = pipeline) {
                            is Pipeline.Media -> pipeline.sampler.resume().getOrThrow()

                            is Pipeline.Audio -> pipeline.sampler.resume().getOrThrow()

                            is Pipeline.Video -> Unit
                        }

                        playbackLoop.start(endOfMedia = { internalState.handlePlaybackCompletion() }).getOrThrow()

                        updateState(
                            InternalState.Loaded.Playing(
                                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                            )
                        )

                        loadedEvents.emit(Event.Playback.Resume)
                    }

                    is Command.Stop -> {
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

                        updateState(
                            InternalState.Loaded.Stopped(
                                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                            )
                        )

                        coroutineScope.launch {
                            loadedEvents.emit(Event.Buffer.Timestamp(0L))

                            loadedEvents.emit(Event.Playback.Timestamp(0L))

                            when (val pipeline = internalState.pipeline) {
                                is Pipeline.Media -> pipeline.renderer.reset().getOrThrow()

                                is Pipeline.Audio -> Unit

                                is Pipeline.Video -> pipeline.renderer.reset().getOrThrow()
                            }
                        }.join()
                    }

                    is Command.SeekTo -> {
                        loadedEvents.emit(Event.Seeking.Start)

                        updateState(
                            InternalState.Loaded.Seeking(
                                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                            )
                        )

                        playbackLoop.stop().getOrThrow()

                        bufferLoop.stop().getOrThrow()

                        when (val pipeline = pipeline) {
                            is Pipeline.Media -> with(pipeline) {
                                sampler.stop().getOrThrow()

                                audioDecoder.seekTo(
                                    micros = command.millis.milliseconds.inWholeMicroseconds
                                ).getOrThrow()

                                videoDecoder.seekTo(
                                    micros = command.millis.milliseconds.inWholeMicroseconds
                                ).getOrThrow()
                            }

                            is Pipeline.Audio -> with(pipeline) {
                                sampler.stop().getOrThrow()

                                decoder.seekTo(
                                    micros = command.millis.milliseconds.inWholeMicroseconds
                                ).getOrThrow()
                            }

                            is Pipeline.Video -> with(pipeline) {
                                decoder.seekTo(
                                    micros = command.millis.milliseconds.inWholeMicroseconds
                                ).getOrThrow()
                            }
                        }

                        bufferLoop.start(
                            onWaiting = ::handleBufferWaiting,
                            endOfMedia = ::handleBufferCompletion
                        ).getOrThrow()

                        playbackLoop.start(endOfMedia = { internalState.handlePlaybackCompletion() }).getOrThrow()

                        loadedEvents.emit(Event.Seeking.Complete)

                        updateState(
                            InternalState.Loaded.Playing(
                                media = media, pipeline = pipeline, bufferLoop = bufferLoop, playbackLoop = playbackLoop
                            )
                        )
                    }
                }
            }
        }
    }.onFailure { t -> handleCommonError(t) }.getOrDefault(Unit)

    override suspend fun release() = runCatching {
        check(internalState.value is InternalState.Loaded) { "Unable to release empty controller" }

        when (val internalState = internalState.value) {
            is InternalState.Empty -> Unit

            is InternalState.Loaded -> with(internalState) {
                bufferTimestampJob?.cancelAndJoin()
                bufferTimestampJob = null

                playbackTimestampJob?.cancelAndJoin()
                playbackTimestampJob = null

                playbackLoop.close()

                bufferLoop.close()

                pipeline.close()
            }
        }

        updateState(InternalState.Empty)
    }.onFailure { t -> handleCommonError(t) }.getOrDefault(Unit)

    override fun close() = runCatching {
        coroutineScope.cancel()

        when (val internalState = internalState.value) {
            is InternalState.Empty -> Unit

            is InternalState.Loaded -> with(internalState) {
                playbackLoop.close()
                bufferLoop.close()
                pipeline.close()
            }
        }
    }.getOrDefault(Unit)
}