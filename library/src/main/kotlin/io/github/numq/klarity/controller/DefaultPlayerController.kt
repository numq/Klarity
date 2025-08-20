package io.github.numq.klarity.controller

import io.github.numq.klarity.buffer.BufferFactory
import io.github.numq.klarity.command.Command
import io.github.numq.klarity.controller.PlayerController.Companion.MAX_PLAYBACK_SPEED_FACTOR
import io.github.numq.klarity.controller.PlayerController.Companion.MIN_PLAYBACK_SPEED_FACTOR
import io.github.numq.klarity.decoder.AudioDecoderFactory
import io.github.numq.klarity.decoder.Decoder
import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.event.PlayerEvent
import io.github.numq.klarity.format.Format
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.loop.buffer.BufferLoopFactory
import io.github.numq.klarity.loop.playback.PlaybackLoopFactory
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
import java.util.concurrent.atomic.AtomicReference
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
    private val samplerFactory: SamplerFactory
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

            pipeline.audioPipeline?.sampler?.stop()?.getOrThrow()
        }
    }

    /**
     * Renderer
     */

    private val rendererRef = AtomicReference<Renderer?>(null)

    private fun getRenderer() = rendererRef.get()

    private suspend fun renderNextFrame(videoPipeline: Pipeline.VideoPipeline) {
        val renderer = getRenderer() ?: return

        with(videoPipeline) {
            val data = pool.acquire().getOrThrow()

            try {
                val frame = decoder.decodeVideo(data = data).getOrThrow() as? Frame.Content.Video

                if (frame != null) {
                    renderer.render(frame).getOrThrow()
                }
            } finally {
                pool.release(item = data).getOrThrow()
            }
        }
    }

    override suspend fun attachRenderer(renderer: Renderer) = runCatching {
        rendererRef.set(renderer)
    }

    override suspend fun detachRenderer() = runCatching {
        rendererRef.getAndSet(null)
    }

    /**
     * Command
     */

    private val commandMutex = Mutex()

    private suspend fun createAudioPipeline(
        location: String, audioBufferSize: Int, format: Format.Audio
    ): Pipeline.AudioPipeline {
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

        return Pipeline.AudioPipeline(
            decoder = decoder, buffer = buffer, sampler = sampler
        )
    }

    private suspend fun createVideoPipeline(
        location: String,
        videoBufferSize: Int,
        hardwareAccelerationCandidates: List<HardwareAcceleration>?,
        format: Format.Video
    ): Pipeline.VideoPipeline {
        val pool = poolFactory.create(
            parameters = PoolFactory.Parameters(poolCapacity = videoBufferSize, createData = {
                Data.makeUninitialized(format.bufferCapacity)
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

        return Pipeline.VideoPipeline(decoder = decoder, pool = pool, buffer = buffer)
    }

    private suspend fun handlePrepare(
        location: String,
        audioBufferSize: Int,
        videoBufferSize: Int,
        hardwareAccelerationCandidates: List<HardwareAcceleration>?,
    ) = coroutineScope {
        val media = Decoder.probe(
            location = location, findAudioStream = audioBufferSize > 0, findVideoStream = videoBufferSize > 0
        ).getOrThrow()

        check(!media.duration.isNegative()) { "Media does not support playback" }

        val deferredAudio = async {
            media.audioFormat?.let { format ->
                createAudioPipeline(
                    location = location, audioBufferSize = audioBufferSize, format = format
                )
            }
        }

        val deferredVideo = async {
            media.videoFormat?.let { format ->
                createVideoPipeline(
                    location = location,
                    videoBufferSize = videoBufferSize,
                    hardwareAccelerationCandidates = hardwareAccelerationCandidates,
                    format = format
                )
            }
        }

        val (closeableAudioPipeline, closeableVideoPipeline) = awaitAll(deferredAudio, deferredVideo)

        val audioPipeline = closeableAudioPipeline as? Pipeline.AudioPipeline

        val videoPipeline = closeableVideoPipeline as? Pipeline.VideoPipeline

        var renderJob: Job? = null

        if (videoPipeline != null) {
            renderJob = launch {
                renderNextFrame(videoPipeline = videoPipeline)
            }
        }

        val pipeline = Pipeline(
            media = media, audioPipeline = audioPipeline, videoPipeline = videoPipeline
        )

        val bufferLoop = bufferLoopFactory.create(
            parameters = BufferLoopFactory.Parameters(pipeline = pipeline)
        ).onFailure {
            pipeline.close().getOrThrow()

            throw it
        }.getOrThrow()

        val playbackLoop = playbackLoopFactory.create(
            parameters = PlaybackLoopFactory.Parameters(
                media = media,
                pipeline = pipeline,
                syncThreshold = SYNC_THRESHOLD,
                getVolume = { if (settings.value.isMuted) 0f else settings.value.volume },
                getPlaybackSpeedFactor = { settings.value.playbackSpeedFactor },
                getRenderer = ::getRenderer
            )
        ).onFailure {
            bufferLoop.close().getOrThrow()

            pipeline.close().getOrThrow()

            throw it
        }.getOrThrow()

        renderJob?.join()

        Triple(pipeline, bufferLoop, playbackLoop)
    }

    private suspend fun InternalPlayerState.Ready.handlePlay() {
        pipeline.audioPipeline?.sampler?.start()?.getOrThrow()

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

        pipeline.audioPipeline?.sampler?.stop()?.getOrThrow()
    }

    private suspend fun InternalPlayerState.Ready.handleResume() {
        pipeline.audioPipeline?.sampler?.start()?.getOrThrow()

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

        val audioJob = pipeline.audioPipeline?.run {
            controllerScope.launch {
                sampler.flush().getOrThrow()

                buffer.clear().getOrThrow()

                decoder.reset().getOrThrow()
            }
        }

        val videoJob = pipeline.videoPipeline?.run videoPipeline@{
            controllerScope.launch {
                buffer.clear().getOrThrow()

                pool.reset().getOrThrow()

                decoder.reset().getOrThrow()

                renderNextFrame(videoPipeline = this@videoPipeline)
            }
        }

        listOfNotNull(audioJob, videoJob).joinAll()

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

            val audioJob = pipeline.audioPipeline?.run {
                controllerScope.launch {
                    sampler.flush().getOrThrow()

                    buffer.clear().getOrThrow()

                    decoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()
                }
            }

            val videoJob = pipeline.videoPipeline?.run {
                controllerScope.launch {
                    buffer.clear().getOrThrow()

                    pool.reset().getOrThrow()

                    decoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()
                }
            }

            listOfNotNull(audioJob, videoJob).joinAll()

            var timestampAfterSeek = timestamp

            when {
                pipeline.audioPipeline != null && pipeline.videoPipeline != null -> {
                    val audioPipeline = pipeline.audioPipeline as Pipeline.AudioPipeline

                    val videoPipeline = pipeline.videoPipeline as Pipeline.VideoPipeline

                    val audioDecoder = audioPipeline.decoder

                    val audioBuffer = audioPipeline.buffer

                    val videoDecoder = videoPipeline.decoder

                    val videoPool = videoPipeline.pool

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
                                val videoFrame =
                                    videoDecoder.decodeVideo(data = data).getOrThrow() as? Frame.Content.Video ?: break

                                videoTimestamp = videoFrame.timestamp

                                when {
                                    audioTimestamp == null -> break

                                    videoFrame.timestamp - audioTimestamp < -SYNC_THRESHOLD -> continue
                                }

                                rendererRef.get()?.render(videoFrame)?.getOrThrow()

                                break
                            } finally {
                                videoPool.release(item = data).getOrThrow()
                            }
                        }
                    }.join()

                    timestampAfterSeek = listOfNotNull(audioTimestamp, videoTimestamp).max()
                }

                pipeline.audioPipeline != null -> (pipeline.audioPipeline)?.run {
                    decoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()

                    val frame = decoder.decodeAudio().getOrThrow()

                    buffer.put(item = frame).getOrThrow()

                    (frame as? Frame.Content.Audio)?.timestamp?.let { timestamp ->
                        timestampAfterSeek = timestamp
                    }
                }

                pipeline.videoPipeline != null -> pipeline.videoPipeline?.run {
                    decoder.seekTo(timestamp = timestamp, keyFramesOnly = true).getOrThrow()

                    val data = pool.acquire().getOrThrow()

                    try {
                        val frame = decoder.decodeVideo(data = data).getOrThrow() as? Frame.Content.Video

                        if (frame != null) {
                            rendererRef.get()?.render(frame)?.getOrThrow()

                            timestampAfterSeek = frame.timestamp
                        }
                    } finally {
                        pool.release(item = data).getOrThrow()
                    }
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
    }

    private val _events = Channel<PlayerEvent>(Channel.BUFFERED)

    override val events = _events.receiveAsFlow()

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
                        } finally {
                            bufferTimestamp.emit(Duration.ZERO)

                            playbackTimestamp.emit(Duration.ZERO)
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
                        is InternalPlayerState.Ready.Playing, is InternalPlayerState.Ready.Paused, is InternalPlayerState.Ready.Stopped, is InternalPlayerState.Ready.Completed -> with(
                            state
                        ) {
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

                        try {
                            state.handleRelease()

                            updateInternalState(InternalPlayerState.Empty)
                        } catch (t: Throwable) {
                            updateInternalState(InternalPlayerState.Error(cause = t, previous = state))
                        } finally {
                            bufferTimestamp.emit(Duration.ZERO)

                            playbackTimestamp.emit(Duration.ZERO)
                        }
                    }

                    else -> return@runCatching
                }
            }
        }
    }

    override suspend fun close() = commandMutex.withLock {
        runCatching {
            controllerScope.cancel()

            when (val state = internalState.value) {
                is InternalPlayerState.Ready -> with(state) {
                    playbackLoop.close().getOrThrow()

                    bufferLoop.close().getOrThrow()

                    pipeline.close().getOrThrow()
                }

                else -> return@runCatching
            }
        }
    }
}