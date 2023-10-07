package player

import audio.AudioSampler
import buffer.BufferManager
import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

interface PlayerController : AutoCloseable {

    val info: MediaInfo
    val events: Flow<PlayerEvent>
    val state: StateFlow<PlayerState>
    val status: StateFlow<PlaybackStatus>
    val pixels: StateFlow<ByteArray?>
    fun updateState(newState: PlayerState)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(timestampMillis: Long)
    fun toggleMute()
    fun changeVolume(value: Double)

    companion object {
        fun create(
            url: String,
            decodeVideo: Boolean,
            decodeAudio: Boolean,
        ): PlayerController = Implementation(Decoder.create(url, decodeVideo = decodeVideo, decodeAudio = decodeAudio))
    }

    class Implementation(private val decoder: Decoder) : PlayerController {

        /**
         * Coroutines
         */

        private val playerContext = Dispatchers.Default + Job()

        private val playerScope = CoroutineScope(playerContext)

        private var playbackJob: Job? = null

        /**
         * Info
         */

        override val info = decoder.info

        /**
         * Event
         */

        private val _event = Channel<PlayerEvent>()

        private fun sendEvent(newEvent: PlayerEvent) {
            _event.trySend(newEvent)
        }

        override val events = _event.receiveAsFlow()

        /**
         * State
         */

        private val _state = MutableStateFlow(PlayerState())

        override fun updateState(newState: PlayerState) {
            _state.value = newState
        }

        override val state = _state.asStateFlow()

        /**
         * Status
         */

        private val _status = MutableStateFlow(PlaybackStatus.STOPPED)

        private fun updateStatus(newStatus: PlaybackStatus) {
            if (status.value != newStatus) _status.value = newStatus
        }

        override val status = _status.asStateFlow()

        /**
         * Pixels
         */

        private val _pixels = MutableStateFlow<ByteArray?>(null)
        override val pixels = _pixels.asStateFlow()

        /**
         * Implementation
         */

        private val buffer = when {
            decoder.hasAudio() && decoder.hasVideo() -> BufferManager.createAudioVideoBuffer(decoder)
            decoder.hasAudio() -> BufferManager.createAudioOnlyBuffer(decoder)
            decoder.hasVideo() -> BufferManager.createVideoOnlyBuffer(decoder)
            else -> throw Exception("Unable to load media")
        }

        private val audioSampler = AudioSampler.create(info.audioFormat)

        override fun play() {
            when (status.value) {
                PlaybackStatus.LOADING, PlaybackStatus.BUFFERING, PlaybackStatus.PLAYING, PlaybackStatus.SEEKING -> return
                else -> sendEvent(PlayerEvent.Play)
            }
        }

        override fun pause() {
            when (status.value) {
                PlaybackStatus.PAUSED -> return
                else -> sendEvent(PlayerEvent.Pause)
            }
        }

        override fun stop() {
            when (status.value) {
                PlaybackStatus.STOPPED -> return
                else -> sendEvent(PlayerEvent.Stop)
            }
        }

        override fun seekTo(timestampMillis: Long) {
            when (status.value) {
                PlaybackStatus.SEEKING -> return
                else -> sendEvent(PlayerEvent.SeekTo(timestampMillis))
            }
        }

        override fun toggleMute() {
            playerScope.launch {
                val isMuted = !state.value.isMuted
                audioSampler.setMuted(isMuted)
                updateState(state.value.copy(isMuted = isMuted))
            }
        }

        override fun changeVolume(value: Double) {
            playerScope.launch {
                audioSampler.setVolume(value)
                updateState(state.value.copy(volume = value))
            }
        }

        override fun close() {
            audioSampler.stop()
            audioSampler.close()

            playbackJob?.cancel()
            playbackJob = null

            _event.close()

            decoder.close()
        }

        private suspend fun startPlayback() {
            playbackJob = CoroutineScope(currentCoroutineContext()).launch {

                audioSampler.apply {
                    start()
                    setMuted(state.value.isMuted)
                    setVolume(state.value.volume)
                }

                buffer.apply {

                    buffer.startBuffering()

                    val hasFramesToPlay = AtomicBoolean(false)

                    var currentAudioFrameTimestampNanos: Long? = null

                    fun renderVideo(frame: DecodedFrame.Video) {
                        _pixels.value = frame.bytes
                    }

                    fun playAudio(frame: DecodedFrame.Audio) {
                        audioSampler.play(frame.bytes)
                        currentAudioFrameTimestampNanos = frame.timestampNanos
                    }

                    when {
                        decoder.hasVideo() && decoder.hasAudio() -> {
                            launch {
                                while (isActive) {
                                    if (hasFramesToPlay.get()) extractVideoFrame()?.let { frame ->
                                        currentAudioFrameTimestampNanos?.let { currentTimestampNanos ->
                                            (frame.timestampNanos - currentTimestampNanos)
                                                .nanoseconds
                                                .takeIf { it.isPositive() }
                                                ?.let { delay(it) }
                                        }
                                        renderVideo(frame)
                                    }
                                }
                            }
                            launch {
                                while (isActive) {
                                    if (hasFramesToPlay.get()) extractAudioFrame()?.let { playAudio(it) }
                                }
                            }
                        }

                        decoder.hasVideo() -> launch {
                            while (isActive) {
                                if (hasFramesToPlay.get()) extractVideoFrame()?.let { frame ->
                                    buffer.firstVideoFrame.value?.timestampNanos?.let { nextTimestampNanos ->
                                        (nextTimestampNanos - frame.timestampNanos)
                                            .nanoseconds
                                            .takeIf { it.isPositive() }
                                            ?.let { delay(it) }
                                    }
                                    renderVideo(frame)
                                }
                            }
                        }

                        decoder.hasAudio() -> launch {
                            while (isActive) {
                                if (hasFramesToPlay.get()) extractAudioFrame()?.let { playAudio(it) }
                            }
                        }
                    }

                    while (isActive) {
                        when {
                            decoder.hasAudio() && decoder.hasVideo() -> hasFramesToPlay.set(audioBufferIsNotEmpty() && videoBufferIsNotEmpty())
                            decoder.hasAudio() -> hasFramesToPlay.set(audioBufferIsNotEmpty())
                            decoder.hasVideo() -> hasFramesToPlay.set(videoBufferIsNotEmpty())
                        }
                        if (hasFramesToPlay.get()) {
                            if (status.value != PlaybackStatus.PLAYING) updateStatus(PlaybackStatus.PLAYING)
                            currentAudioFrameTimestampNanos?.run {
                                updateState(state.value.copy(bufferTimestampMillis = maxOf(
                                    lastAudioFrame.value?.timestampNanos ?: -1L,
                                    lastVideoFrame.value?.timestampNanos ?: -1L
                                ).takeIf { it >= 0L } ?: state.value.bufferTimestampMillis,
                                    playbackTimestampMillis = nanoseconds.inWholeMilliseconds))
                            }
                        } else {
                            if (isCompleted) {
                                joinAll()
                                return@launch sendEvent(PlayerEvent.Complete)
                            } else updateStatus(PlaybackStatus.BUFFERING)
                        }

                        delay((1 / info.frameRate * 1_000L).milliseconds)
                    }
                }
            }
        }

        private suspend fun stopPlayback(flushBuffer: Boolean = true) {
            audioSampler.stop()

            playbackJob?.cancelAndJoin().also { println("Playback job has been cancelled") }
            playbackJob = null
            if (flushBuffer) buffer.flush()
        }

        init {
            events.onEach(::println).onEach { event ->
                when (event) {
                    is PlayerEvent.Play -> {
                        updateStatus(PlaybackStatus.LOADING)
                        startPlayback()
                    }

                    is PlayerEvent.Pause -> {
                        updateStatus(PlaybackStatus.LOADING)
                        stopPlayback(flushBuffer = false)
                        updateStatus(PlaybackStatus.PAUSED)
                    }

                    is PlayerEvent.Stop -> {
                        updateStatus(PlaybackStatus.LOADING)
                        stopPlayback()
                        decoder.stop()
                        updateStatus(PlaybackStatus.STOPPED)
                        updateState(
                            state.value.copy(
                                bufferTimestampMillis = 0L, playbackTimestampMillis = 0L
                            )
                        )
                    }

                    is PlayerEvent.Complete -> {
                        updateStatus(PlaybackStatus.LOADING)
                        decoder.stop()
                        updateState(
                            state.value.copy(
                                bufferTimestampMillis = 0L, playbackTimestampMillis = 0L
                            )
                        )
                        stopPlayback()
                        updateStatus(PlaybackStatus.STOPPED)
                    }

                    is PlayerEvent.SeekTo -> {
                        updateStatus(PlaybackStatus.LOADING)
                        stopPlayback()
                        updateStatus(PlaybackStatus.SEEKING)
                        val timestampToSeekMicros = event.timestampMillis.milliseconds.inWholeNanoseconds.coerceIn(
                            0L,
                            info.durationNanos
                        ).nanoseconds.inWholeMicroseconds
                        decoder.seekTo(timestampToSeekMicros)
                        startPlayback()
                    }
                }
            }.launchIn(playerScope)
        }
    }
}