package player

import audio.AudioSampler
import buffer.BufferManager
import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import synchronizer.TimestampSynchronizer
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

interface PlayerController : AutoCloseable {

    val error: Flow<Exception>
    val state: StateFlow<PlayerState>
    val status: StateFlow<PlaybackStatus>
    val videoFrame: StateFlow<DecodedFrame.Video?>
    suspend fun toggleMute()
    suspend fun changeVolume(value: Float)
    suspend fun load(mediaUrl: String, bufferDurationMillis: Long? = null)
    suspend fun seekTo(timestampMillis: Long)
    suspend fun play()
    suspend fun pause()
    suspend fun stop()

    companion object {
        private const val DEFAULT_BUFFER_DURATION_MILLIS = 1_000L

        fun create(): PlayerController = Implementation()
    }

    private class Implementation : PlayerController {

        private val playerContext = Dispatchers.Default + SupervisorJob()
        private val playerScope = CoroutineScope(playerContext)
        private var bufferingJob: Job? = null
        private var playbackJob: Job? = null

        private val _error = Channel<Exception>(Channel.BUFFERED)
        private val _state = MutableStateFlow(PlayerState())
        private val _status = MutableStateFlow(PlaybackStatus.EMPTY)
        private var _videoFrame = MutableStateFlow<DecodedFrame.Video?>(null)

        override val error: Flow<Exception> = _error.consumeAsFlow().onEach { println(it.localizedMessage) }
        override val state: StateFlow<PlayerState> = _state.asStateFlow()
        override val status: StateFlow<PlaybackStatus> = _status.asStateFlow()
        override val videoFrame: StateFlow<DecodedFrame.Video?> = _videoFrame.asStateFlow()

        private var decoder: Decoder? = null
        private var buffer: BufferManager? = null
        private var audioSampler: AudioSampler? = null

        private val interactionMutex = Mutex()
        private val synchronizer = TimestampSynchronizer()

        private var isCompleted = false

        private suspend fun startBuffering(previewFrame: suspend (DecodedFrame.Video) -> Unit) {
            buffer?.runCatching {
                bufferingJob?.cancelAndJoin()
                bufferingJob = playerScope.launch {
                    var firstFrameReceived = false
                    startBuffering().onCompletion { throwable -> isCompleted = throwable == null }
                        .collect { bufferTimestampNanos ->
                            if (!firstFrameReceived) {
                                if (firstVideoFrame() is DecodedFrame.Video) {
                                    previewFrame(firstVideoFrame() as DecodedFrame.Video)
                                    firstFrameReceived = true
                                }
                            }
                            _state.emit(
                                state.value.copy(
                                    bufferTimestampMillis = bufferTimestampNanos.nanoseconds.inWholeMilliseconds
                                )
                            )
                        }
                }
            }?.onFailure { _error.send(Exception(it)) }
        }

        private suspend fun startPlayback() {
            buffer?.runCatching {

                isCompleted = false

                playbackJob?.cancelAndJoin()
                playbackJob = playerScope.launch playback@{

                    val media = state.value.media ?: return@playback _error.send(Exception("Unable to load media"))

                    val videoJob = if (media.videoFrameRate > 0.0) launch video@{
                        while (isActive) {
                            if (status.value == PlaybackStatus.PLAYING) {
                                when (val frame = extractVideoFrame()) {
                                    null -> if (isCompleted) return@video
                                    is DecodedFrame.Audio -> Unit

                                    is DecodedFrame.Video -> {

                                        _state.emit(
                                            state.value.copy(
                                                playbackTimestampMillis = frame.timestampNanos.nanoseconds.inWholeMilliseconds
                                            )
                                        )

                                        synchronizer.updateVideoTimestamp(frame.timestampNanos)

                                        if (media.audioFrameRate > 0.0) synchronizer.syncWithAudio() else synchronizer.syncWithVideo()

                                        _videoFrame.value = frame
                                    }

                                    is DecodedFrame.End -> return@video
                                }
                            }
                        }
                    } else null

                    val audioJob = if (media.audioFrameRate > 0.0) launch audio@{
                        while (isActive) {
                            if (status.value == PlaybackStatus.PLAYING) {
                                when (val frame = extractAudioFrame()) {
                                    null -> if (isCompleted) return@audio

                                    is DecodedFrame.Audio -> {

                                        _state.emit(
                                            state.value.copy(
                                                playbackTimestampMillis = frame.timestampNanos.nanoseconds.inWholeMilliseconds
                                            )
                                        )

                                        synchronizer.updateAudioTimestamp(frame.timestampNanos)

                                        audioSampler?.play(frame.bytes)
                                    }

                                    is DecodedFrame.Video -> Unit

                                    is DecodedFrame.End -> return@audio
                                }
                            }
                        }
                    } else null

                    joinAll(*listOfNotNull(audioJob, videoJob).toTypedArray())

                    println("End of media")

                    _state.emit(
                        state.value.copy(
                            playbackTimestampMillis = media.durationNanos.nanoseconds.inWholeMilliseconds
                        )
                    )

                    _status.emit(PlaybackStatus.PAUSED)
                }
            }?.onFailure { _error.send(Exception(it)) }
        }

        override suspend fun toggleMute() {
            runCatching {
                audioSampler?.setMuted(!state.value.isMuted)?.let { isMuted ->
                    _state.emit(state.value.copy(isMuted = isMuted))
                }
            }.onFailure { _error.send(Exception(it)) }
        }

        override suspend fun changeVolume(value: Float) {
            runCatching {
                audioSampler?.setVolume(value)?.let { volume ->
                    _state.emit(state.value.copy(volume = volume))
                }
            }.onFailure { _error.send(Exception(it)) }
        }

        override suspend fun load(mediaUrl: String, bufferDurationMillis: Long?) = interactionMutex.withLock {
            runCatching {
                playbackJob?.cancelAndJoin()
                playbackJob = null

                bufferingJob?.cancelAndJoin()
                bufferingJob = null

                _videoFrame.value = null

                decoder?.close()
                decoder = Decoder.create(mediaUrl)

                decoder?.run {
                    buffer = BufferManager.create(this, bufferDurationMillis ?: DEFAULT_BUFFER_DURATION_MILLIS)

                    media.audioFormat?.let { audioFormat ->
                        audioSampler = AudioSampler.create(audioFormat)
                    }

                    _state.emit(
                        state.value.copy(
                            media = media, bufferTimestampMillis = 0L, playbackTimestampMillis = 0L
                        )
                    )

                    _status.emit(PlaybackStatus.PAUSED)

                    audioSampler?.start()

                    restart()

                    startBuffering(_videoFrame::emit)

                    startPlayback()
                } ?: throw Exception("Unable to load media")
            }
        }.onFailure { _error.send(Exception(it)) }.getOrDefault(Unit)

        override suspend fun seekTo(timestampMillis: Long) = interactionMutex.withLock {
            runCatching {
                when (status.value) {
                    PlaybackStatus.PLAYING, PlaybackStatus.PAUSED, PlaybackStatus.STOPPED -> {
                        val initialStatus = status.value

                        bufferingJob?.cancelAndJoin()
                        bufferingJob = null

                        playbackJob?.cancelAndJoin()
                        playbackJob = null

                        _status.emit(PlaybackStatus.SEEKING)

                        audioSampler?.stop()

                        buffer?.flush()

                        decoder?.seekTo(timestampMillis.milliseconds.inWholeMicroseconds)?.microseconds?.inWholeMilliseconds?.let { timestamp ->
                            _state.emit(
                                state.value.copy(
                                    bufferTimestampMillis = timestamp, playbackTimestampMillis = timestamp
                                )
                            )
                        }

                        startBuffering(_videoFrame::emit)

                        audioSampler?.start()

                        startPlayback()

                        when (initialStatus) {
                            PlaybackStatus.PLAYING -> _status.emit(PlaybackStatus.PLAYING)

                            PlaybackStatus.PAUSED, PlaybackStatus.STOPPED -> _status.emit(PlaybackStatus.PAUSED)

                            else -> Unit
                        }
                    }

                    else -> Unit
                }
            }.onFailure { _error.send(Exception(it)) }.getOrDefault(Unit)
        }

        override suspend fun play() = interactionMutex.withLock {
            runCatching {

                if (state.value.playbackTimestampMillis.milliseconds.inWholeNanoseconds == state.value.media?.durationNanos) return@runCatching

                when (status.value) {
                    PlaybackStatus.PAUSED -> _status.emit(PlaybackStatus.PLAYING)

                    PlaybackStatus.STOPPED -> {
                        startBuffering(_videoFrame::emit)

                        audioSampler?.start()

                        startPlayback()

                        _status.emit(PlaybackStatus.PLAYING)
                    }

                    else -> Unit
                }
            }
        }.onFailure { _error.send(Exception(it)) }.getOrDefault(Unit)

        override suspend fun pause() = interactionMutex.withLock {
            runCatching {
                when (status.value) {
                    PlaybackStatus.PLAYING -> _status.emit(PlaybackStatus.PAUSED)

                    else -> Unit
                }
            }.onFailure { _error.send(Exception(it)) }.getOrDefault(Unit)
        }

        override suspend fun stop() = interactionMutex.withLock {
            runCatching {
                when (status.value) {
                    PlaybackStatus.PLAYING, PlaybackStatus.PAUSED -> {
                        playbackJob?.cancelAndJoin()
                        playbackJob = null

                        bufferingJob?.cancelAndJoin()
                        bufferingJob = null

                        _status.emit(PlaybackStatus.STOPPED)

                        audioSampler?.stop()

                        buffer?.flush()

                        _state.emit(state.value.copy(bufferTimestampMillis = 0L, playbackTimestampMillis = 0L))
                    }

                    else -> Unit
                }
            }.onFailure { _error.send(Exception(it)) }.getOrDefault(Unit)
        }

        override fun close() {
            playbackJob?.cancel()
            playbackJob = null

            bufferingJob?.cancel()
            bufferingJob = null

            audioSampler?.close()
            decoder?.close()
        }
    }
}