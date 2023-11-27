package player

import audio.AudioSampler
import buffer.BufferManager
import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import synchronizer.TimestampSynchronizer
import kotlin.time.Duration.Companion.microseconds
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

/**
 * Interface representing a player controller for managing media playback.
 */
interface PlayerController : AutoCloseable {

    /**
     * Flow emitting exceptions that occur during playback.
     */
    val error: Flow<Exception>

    /**
     * StateFlow representing the current state of the player.
     */
    val state: StateFlow<PlayerState>

    /**
     * StateFlow representing the current playback status.
     */
    val status: StateFlow<PlaybackStatus>

    /**
     * StateFlow representing the current video frame being displayed.
     */
    val videoFrame: StateFlow<DecodedFrame.Video?>

    /**
     * Toggles the mute state of the audio.
     */
    suspend fun toggleMute()

    /**
     * Changes the volume of the audio.
     * @param value The new volume level, ranging from 0.0 to 1.0.
     */
    suspend fun changeVolume(value: Float)

    /**
     * Loads and prepares the player for playback of the specified media.
     * @param mediaUrl The url of the media to load.
     * @param bufferDurationMillis The duration, in milliseconds, for which to buffer frames.
     */
    suspend fun load(mediaUrl: String, bufferDurationMillis: Long? = null)

    /**
     * Seeks to the specified timestamp in milliseconds.
     * @param timestampMillis The timestamp to seek to, in milliseconds.
     */
    suspend fun seekTo(timestampMillis: Long)

    /**
     * Resumes or starts playback.
     */
    suspend fun play()

    /**
     * Pauses the current playback.
     */
    suspend fun pause()

    /**
     * Stops the current playback.
     */
    suspend fun stop()

    /**
     * Companion object providing a factory method to create a [PlayerController] instance.
     */
    companion object {
        private const val DEFAULT_BUFFER_DURATION_MILLIS = 1_000L

        /**
         * Creates a [PlayerController] instance.
         * @return A [PlayerController] instance.
         */
        fun create(): PlayerController = Implementation()
    }

    private class Implementation : PlayerController {

        private val playerContext = Dispatchers.Default + SupervisorJob()

        private val playerScope = CoroutineScope(playerContext)

        private var bufferingJob: Job? = null

        private var playbackJob: Job? = null

        private val _error = Channel<Exception>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val error: Flow<Exception> = _error.consumeAsFlow()

        private val _state = MutableStateFlow(PlayerState())

        override val state: StateFlow<PlayerState> = _state.asStateFlow()

        private val _status = Channel<PlaybackStatus>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val status: StateFlow<PlaybackStatus> = _status.consumeAsFlow()
            .stateIn(playerScope, SharingStarted.Eagerly, PlaybackStatus.EMPTY)

        private var _videoFrame = Channel<DecodedFrame.Video?>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val videoFrame: StateFlow<DecodedFrame.Video?> = _videoFrame.consumeAsFlow()
            .stateIn(playerScope, SharingStarted.Eagerly, null)

        private var decoder: Decoder? = null

        private var buffer: BufferManager? = null

        private var audioSampler: AudioSampler? = null

        private val interactionMutex = Mutex()

        private val synchronizer = TimestampSynchronizer.create()

        private var previewFrame: DecodedFrame.Video? = null

        private var isCompleted = false

        private suspend fun startBuffering() {
            var previewRendered = false
            buffer?.runCatching {
                bufferingJob?.cancelAndJoin()
                bufferingJob = playerScope.launch {
                    startBuffering()
                        .onCompletion { throwable ->
                            isCompleted = throwable == null
                        }
                        .collect { bufferTimestampNanos ->
                            if (!previewRendered && state.value.media?.videoFrameRate?.compareTo(0.0) == 1) {
                                (firstVideoFrame() as? DecodedFrame.Video)?.let { frame ->
                                    previewRendered = _videoFrame.trySend(frame).isSuccess
                                    previewFrame = frame
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

                                        _videoFrame.trySend(frame)
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

                    _status.send(PlaybackStatus.COMPLETED)
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
                    _state.emit(state.value.copy(volume = volume, isMuted = false))
                }
            }.onFailure { _error.send(Exception(it)) }
        }

        override suspend fun load(mediaUrl: String, bufferDurationMillis: Long?) = interactionMutex.withLock {
            runCatching {
                playbackJob?.cancelAndJoin()
                playbackJob = null

                bufferingJob?.cancelAndJoin()
                bufferingJob = null

                decoder?.close()

                previewFrame = null

                _status.send(PlaybackStatus.EMPTY)

                decoder = Decoder.create(mediaUrl)

                decoder?.run {

                    if (media.videoFrameRate <= 0.0) _videoFrame.send(null)

                    _state.emit(
                        state.value.copy(
                            media = media, bufferTimestampMillis = 0L, playbackTimestampMillis = 0L
                        )
                    )

                    buffer = BufferManager.create(this, bufferDurationMillis ?: DEFAULT_BUFFER_DURATION_MILLIS)

                    media.audioFormat?.let { audioFormat ->
                        audioSampler = AudioSampler.create(audioFormat)
                    }

                    restart()

                    startBuffering()

                    audioSampler?.start()

                    startPlayback()

                    _status.send(PlaybackStatus.LOADED)
                } ?: throw Exception("Unable to load media")
            }
        }.onFailure { _error.send(Exception(it)) }.getOrDefault(Unit)

        override suspend fun seekTo(timestampMillis: Long) = interactionMutex.withLock {
            runCatching {
                when (status.value) {
                    PlaybackStatus.LOADED, PlaybackStatus.PLAYING, PlaybackStatus.PAUSED, PlaybackStatus.STOPPED, PlaybackStatus.COMPLETED -> {
                        val initialStatus = status.value

                        bufferingJob?.cancelAndJoin()
                        bufferingJob = null

                        playbackJob?.cancelAndJoin()
                        playbackJob = null

                        audioSampler?.stop()

                        buffer?.flush()

                        _status.send(PlaybackStatus.SEEKING)

                        decoder?.seekTo(timestampMillis.milliseconds.inWholeMicroseconds)?.microseconds?.inWholeMilliseconds?.let { timestamp ->
                            _state.emit(
                                state.value.copy(
                                    bufferTimestampMillis = timestamp, playbackTimestampMillis = timestamp
                                )
                            )
                        }

                        startBuffering()

                        audioSampler?.start()

                        startPlayback()

                        when (initialStatus) {
                            PlaybackStatus.PLAYING -> _status.send(PlaybackStatus.PLAYING)

                            PlaybackStatus.LOADED, PlaybackStatus.PAUSED, PlaybackStatus.STOPPED, PlaybackStatus.COMPLETED -> _status.send(
                                PlaybackStatus.PAUSED
                            )

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
                    PlaybackStatus.LOADED, PlaybackStatus.PAUSED -> _status.send(PlaybackStatus.PLAYING)

                    PlaybackStatus.STOPPED -> {
                        decoder?.restart()

                        startBuffering()

                        audioSampler?.start()

                        startPlayback()

                        _status.send(PlaybackStatus.PLAYING)
                    }

                    else -> Unit
                }
            }
        }.onFailure { _error.send(Exception(it)) }.getOrDefault(Unit)

        override suspend fun pause() = interactionMutex.withLock {
            runCatching {
                when (status.value) {
                    PlaybackStatus.PLAYING -> _status.send(PlaybackStatus.PAUSED)

                    else -> Unit
                }
            }.onFailure { _error.send(Exception(it)) }.getOrDefault(Unit)
        }

        override suspend fun stop() = interactionMutex.withLock {
            runCatching {
                when (status.value) {
                    PlaybackStatus.LOADED, PlaybackStatus.PLAYING, PlaybackStatus.PAUSED, PlaybackStatus.COMPLETED -> {
                        playbackJob?.cancelAndJoin()
                        playbackJob = null

                        bufferingJob?.cancelAndJoin()
                        bufferingJob = null

                        _videoFrame.send(previewFrame)

                        audioSampler?.stop()

                        buffer?.flush()

                        _state.emit(state.value.copy(bufferTimestampMillis = 0L, playbackTimestampMillis = 0L))

                        _status.send(PlaybackStatus.STOPPED)
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

            _error.close()
            _status.close()
            _videoFrame.close()

            audioSampler?.close()
            decoder?.close()
        }
    }
}