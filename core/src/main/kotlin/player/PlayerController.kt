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
    val exception: Flow<Exception>

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
     *
     * @param value The new volume level, ranging from 0.0 to 1.0.
     */
    suspend fun changeVolume(value: Float)

    /**
     * Retrieves a snapshot of the video frame at the specified timestamp in milliseconds.
     *
     * @param timestampMillis The timestamp to capture, in milliseconds.
     * @return The snapshot of the video frame at the specified timestamp, or `null` if not available.
     */
    suspend fun snapshot(timestampMillis: Long): DecodedFrame.Video?

    /**
     * Loads and prepares the player for playback of the specified media.
     *
     * @param mediaUrl The url of the media to load.
     * @param bufferDurationMillis The duration, in milliseconds, for which to buffer frames.
     */
    suspend fun load(mediaUrl: String, bufferDurationMillis: Long? = null)

    /**
     * Unloads the current media, stopping any ongoing playback and releasing resources.
     */
    suspend fun unload()

    /**
     * Seeks to the specified timestamp in milliseconds.
     *
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
         *
         * @return A [PlayerController] instance.
         */
        fun create(): PlayerController = Implementation()
    }

    private class Implementation : PlayerController {

        private val playerContext = Dispatchers.Default + SupervisorJob()

        private val playerScope = CoroutineScope(playerContext)

        private var bufferingJob: Job? = null

        private var playbackJob: Job? = null

        private val _exception = Channel<Exception>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val exception: Flow<Exception> = _exception.consumeAsFlow()

        private val _state = MutableStateFlow(PlayerState())

        override val state: StateFlow<PlayerState> = _state.asStateFlow()

        private val _status = Channel<PlaybackStatus>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val status: StateFlow<PlaybackStatus> = _status.consumeAsFlow()
            .distinctUntilChanged()
            .stateIn(playerScope, SharingStarted.Eagerly, PlaybackStatus.EMPTY)

        private var _videoFrame = Channel<DecodedFrame.Video?>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

        override val videoFrame: StateFlow<DecodedFrame.Video?> = _videoFrame.consumeAsFlow()
            .distinctUntilChanged()
            .stateIn(playerScope, SharingStarted.Eagerly, null)

        private var decoder: Decoder? = null

        private var buffer: BufferManager? = null

        private var audioSampler: AudioSampler? = null

        private val interactionMutex = Mutex()

        private val synchronizer = TimestampSynchronizer.create()

        private var isCompleted = false

        private suspend fun startBuffering() {
            buffer?.runCatching {
                bufferingJob?.cancelAndJoin()
                bufferingJob = playerScope.launch {
                    startBuffering()
                        .onCompletion { throwable -> isCompleted = throwable == null }
                        .collect { bufferTimestampNanos ->
                            _state.emit(
                                state.value.copy(
                                    bufferTimestampMillis = bufferTimestampNanos.nanoseconds.inWholeMilliseconds
                                )
                            )
                        }
                }
            }?.onFailure { _exception.send(Exception(it)) }
        }

        private suspend fun startPlayback() {
            buffer?.runCatching {

                isCompleted = false

                playbackJob?.cancelAndJoin()
                playbackJob = playerScope.launch playback@{

                    val media = state.value.media ?: return@playback _exception.send(Exception("Unable to load media"))

                    val audioJob = if (media.audioFrameRate > 0.0) launch audio@{
                        while (isActive) {
                            if (status.value == PlaybackStatus.PLAYING) {
                                when (val frame = extractAudioFrame()) {
                                    null -> if (isCompleted) return@audio

                                    is DecodedFrame.Audio -> {

                                        synchronizer.updateAudioTimestamp(frame.timestampNanos)

                                        _state.emit(
                                            state.value.copy(
                                                playbackTimestampMillis = frame.timestampNanos.nanoseconds.inWholeMilliseconds
                                            )
                                        )

                                        audioSampler?.play(frame.bytes)
                                    }

                                    is DecodedFrame.Video -> Unit

                                    is DecodedFrame.End -> return@audio
                                }
                            }
                        }
                    } else null

                    val videoJob = if (media.videoFrameRate > 0.0) launch video@{
                        while (isActive) {
                            if (status.value == PlaybackStatus.PLAYING) {
                                when (val frame = extractVideoFrame()) {
                                    null -> if (isCompleted) return@video
                                    is DecodedFrame.Audio -> Unit

                                    is DecodedFrame.Video -> {

                                        synchronizer.updateVideoTimestamp(frame.timestampNanos)

                                        _state.emit(
                                            state.value.copy(
                                                playbackTimestampMillis = frame.timestampNanos.nanoseconds.inWholeMilliseconds
                                            )
                                        )

                                        if (media.audioFrameRate > 0.0) synchronizer.syncWithAudio(media.videoFrameRate)
                                        else synchronizer.syncWithVideo(media.videoFrameRate)

                                        _videoFrame.send(frame)
                                    }

                                    is DecodedFrame.End -> return@video
                                }
                            }
                        }
                    } else null

                    joinAll(*listOfNotNull(audioJob, videoJob).toTypedArray())

                    /**
                     * End of media
                     */

                    _state.emit(
                        state.value.copy(
                            playbackTimestampMillis = media.durationNanos.nanoseconds.inWholeMilliseconds
                        )
                    )

                    _status.send(PlaybackStatus.COMPLETED)
                }
            }?.onFailure { _exception.send(Exception(it)) }
        }

        override suspend fun toggleMute() = runCatching {
            audioSampler?.setMuted(!state.value.isMuted)?.let { isMuted ->
                _state.emit(state.value.copy(isMuted = isMuted))
            } ?: Unit
        }.onFailure { _exception.send(Exception(it)) }.getOrDefault(Unit)

        override suspend fun changeVolume(value: Float) = runCatching {
            audioSampler?.setVolume(value)?.let { volume ->
                _state.emit(state.value.copy(volume = volume, isMuted = false))
            } ?: Unit
        }.onFailure { _exception.send(Exception(it)) }.getOrDefault(Unit)

        override suspend fun snapshot(timestampMillis: Long) = runCatching {
            decoder?.snapshot(timestampMillis.milliseconds.inWholeMicroseconds)
        }.onFailure { _exception.send(Exception(it)) }.getOrNull()

        override suspend fun load(mediaUrl: String, bufferDurationMillis: Long?) = interactionMutex.withLock {
            runCatching {
                playbackJob?.cancelAndJoin()
                playbackJob = null

                bufferingJob?.cancelAndJoin()
                bufferingJob = null

                decoder?.close()

                decoder = Decoder.create(mediaUrl)

                decoder?.run {

                    _videoFrame.send(media.previewFrame)

                    _state.emit(
                        state.value.copy(
                            media = media, bufferTimestampMillis = 0L, playbackTimestampMillis = 0L
                        )
                    )

                    buffer = BufferManager.create(this, bufferDurationMillis ?: DEFAULT_BUFFER_DURATION_MILLIS)

                    media.audioFormat?.let { audioFormat ->
                        audioSampler = AudioSampler.create(audioFormat).apply {
                            setVolume(state.value.volume)
                            setMuted(state.value.isMuted)
                        }
                    }

                    restart()

                    startBuffering()

                    audioSampler?.start()

                    startPlayback()

                    _status.send(PlaybackStatus.LOADED)
                } ?: throw Exception("Unable to load media")
            }
        }.onFailure { _exception.send(Exception(it)) }.getOrDefault(Unit)

        override suspend fun unload() = interactionMutex.withLock {
            runCatching {
                when (status.value) {
                    PlaybackStatus.EMPTY -> Unit

                    else -> {
                        playbackJob?.cancelAndJoin()
                        playbackJob = null

                        bufferingJob?.cancelAndJoin()
                        bufferingJob = null

                        _videoFrame.send(null)

                        audioSampler?.stop()

                        buffer?.flush()

                        _state.emit(
                            state.value.copy(
                                media = null,
                                bufferTimestampMillis = 0L,
                                playbackTimestampMillis = 0L
                            )
                        )

                        _status.send(PlaybackStatus.EMPTY)
                    }
                }
            }
        }.onFailure { _exception.send(Exception(it)) }.getOrDefault(Unit)

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

                        decoder?.seekTo(timestampMillis.milliseconds.inWholeMicroseconds)?.let { timestampMicros ->
                            timestampMicros.microseconds.inWholeMilliseconds.run {
                                _state.emit(
                                    state.value.copy(
                                        bufferTimestampMillis = this,
                                        playbackTimestampMillis = this
                                    )
                                )
                            }

                            decoder?.snapshot(timestampMicros)?.let { frame ->
                                _videoFrame.send(frame)
                            }
                        }

                        startBuffering()

                        audioSampler?.start()

                        startPlayback()

                        when (initialStatus) {
                            PlaybackStatus.PLAYING -> _status.send(PlaybackStatus.PLAYING)

                            PlaybackStatus.LOADED,
                            PlaybackStatus.PAUSED,
                            PlaybackStatus.STOPPED,
                            PlaybackStatus.COMPLETED,
                            -> _status.send(PlaybackStatus.PAUSED)

                            else -> Unit
                        }
                    }

                    else -> Unit
                }
            }
        }.onFailure { _exception.send(Exception(it)) }.getOrDefault(Unit)

        override suspend fun play() = interactionMutex.withLock {
            runCatching {

                if (state.value.playbackTimestampMillis.milliseconds.inWholeNanoseconds == state.value.media?.durationNanos) return@runCatching

                when (status.value) {
                    PlaybackStatus.PAUSED -> {
                        audioSampler?.start()

                        _status.send(PlaybackStatus.PLAYING)
                    }

                    PlaybackStatus.LOADED, PlaybackStatus.STOPPED, PlaybackStatus.COMPLETED -> {
                        decoder?.restart()

                        startBuffering()

                        audioSampler?.start()

                        startPlayback()

                        _status.send(PlaybackStatus.PLAYING)
                    }

                    else -> Unit
                }
            }
        }.onFailure { _exception.send(Exception(it)) }.getOrDefault(Unit)

        override suspend fun pause() = interactionMutex.withLock {
            runCatching {
                when (status.value) {
                    PlaybackStatus.PLAYING -> {
                        audioSampler?.stop()

                        _status.send(PlaybackStatus.PAUSED)
                    }

                    else -> Unit
                }
            }.onFailure { _exception.send(Exception(it)) }.getOrDefault(Unit)
        }

        override suspend fun stop() = interactionMutex.withLock {
            runCatching {
                when (status.value) {
                    PlaybackStatus.LOADED, PlaybackStatus.PLAYING, PlaybackStatus.PAUSED, PlaybackStatus.COMPLETED -> {
                        playbackJob?.cancelAndJoin()
                        playbackJob = null

                        bufferingJob?.cancelAndJoin()
                        bufferingJob = null

                        state.value.media?.previewFrame?.let { frame ->
                            _videoFrame.send(frame)
                        }

                        audioSampler?.stop()

                        buffer?.flush()

                        _state.emit(state.value.copy(bufferTimestampMillis = 0L, playbackTimestampMillis = 0L))

                        _status.send(PlaybackStatus.STOPPED)
                    }

                    else -> Unit
                }
            }
        }.onFailure { _exception.send(Exception(it)) }.getOrDefault(Unit)

        override fun close() {
            playbackJob?.cancel()
            playbackJob = null

            bufferingJob?.cancel()
            bufferingJob = null

            _exception.close()

            _status.close()

            _videoFrame.close()

            audioSampler?.close()

            decoder?.close()
        }
    }
}