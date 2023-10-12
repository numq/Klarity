package player

import audio.AudioSampler
import buffer.BufferManager
import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import media.Media
import media.MediaSettings
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

interface PlayerController : AutoCloseable {

    val events: Flow<PlayerEvent>
    val state: StateFlow<PlayerState>
    val status: StateFlow<PlaybackStatus>
    val media: StateFlow<Media?>
    val pixels: StateFlow<ByteArray?>
    fun load(settings: MediaSettings)
    fun play()
    fun pause()
    fun stop()
    fun seekTo(timestampMillis: Long)
    fun toggleMute()
    fun changeVolume(value: Double)

    companion object {
        fun create(audioBufferSize: Int? = null, videoBufferSize: Int? = null): PlayerController =
            Implementation(audioBufferSize, videoBufferSize)
    }

    class Implementation(
        private val audioBufferCapacity: Int?,
        private val videoBufferCapacity: Int?,
    ) : PlayerController {

        /**
         * Coroutines
         */

        private val playerContext = Dispatchers.Default + Job()

        private val playerScope = CoroutineScope(playerContext)

        private var eventJob: Job? = null

        private var playbackJob: Job? = null

        /**
         * Event
         */

        private val _events = Channel<PlayerEvent>(Channel.CONFLATED)

        private fun sendEvent(newEvent: PlayerEvent) {
            _events.trySend(newEvent)
        }

        override val events = _events.consumeAsFlow()

        /**
         * State
         */

        private val _state = MutableStateFlow(PlayerState())

        private fun updateState(newState: PlayerState) {
            _state.update { newState }
        }

        override val state = _state.asStateFlow()

        /**
         * Status
         */

        private val _status = MutableStateFlow(PlaybackStatus.EMPTY)

        private fun setStatus(newStatus: PlaybackStatus) {
            _status.value = newStatus
        }

        override val status = _status.asStateFlow()

        /**
         * Media
         */

        private val _media = MutableStateFlow<Media?>(null)

        override val media = _media.asStateFlow()

        private fun setMedia(newMedia: Media) {
            _media.value = newMedia
        }

        /**
         * Pixels
         */

        private var _pixels = MutableStateFlow<ByteArray?>(null)

        override val pixels = _pixels.asStateFlow()

        private fun setPixels(newPixels: ByteArray?) {
            _pixels.value = newPixels
        }

        /**
         * Implementation
         */

        private var decoder: Decoder? = null

        private var buffer: BufferManager? = null

        private var audioSampler: AudioSampler? = null

        private var pausedTimestampNanos: Long? = null

        override fun load(settings: MediaSettings) = sendEvent(PlayerEvent.Load(settings))

        override fun play() {
            when (status.value) {
                PlaybackStatus.EMPTY, PlaybackStatus.BUFFERING, PlaybackStatus.PLAYING, PlaybackStatus.SEEKING -> return
                else -> sendEvent(PlayerEvent.Play)
            }
        }

        override fun pause() {
            when (status.value) {
                PlaybackStatus.EMPTY, PlaybackStatus.PAUSED, PlaybackStatus.STOPPED, PlaybackStatus.SEEKING -> return
                else -> sendEvent(PlayerEvent.Pause)
            }
        }

        override fun stop() {
            when (status.value) {
                PlaybackStatus.EMPTY, PlaybackStatus.STOPPED, PlaybackStatus.SEEKING -> return
                else -> sendEvent(PlayerEvent.Stop)
            }
        }

        override fun seekTo(timestampMillis: Long) {
            when (status.value) {
                PlaybackStatus.EMPTY, PlaybackStatus.SEEKING -> return
                else -> sendEvent(PlayerEvent.SeekTo(timestampMillis))
            }
        }

        override fun toggleMute() {
            val isMuted = !state.value.isMuted
            audioSampler?.setMuted(isMuted)
            updateState(state.value.copy(isMuted = isMuted))
        }

        override fun changeVolume(value: Double) {
            audioSampler?.setVolume(value)
            updateState(state.value.copy(volume = value))
        }

        override fun close() {
            audioSampler?.stop()
            audioSampler?.close()
            audioSampler = null

            playbackJob?.cancel()
            playbackJob = null

            _events.cancel()

            buffer = null

            decoder?.close()
            decoder = null
        }

        private fun renderVideo(frame: DecodedFrame.Video) {
            setPixels(frame.bytes)
        }

        private fun playAudio(frame: DecodedFrame.Audio) {
            audioSampler?.play(frame.bytes)
        }

        private suspend fun startPlayback() {
            playbackJob = playerScope.launch {

                val hasFramesToPlay = AtomicBoolean(false)

                var currentAudioFrameTimestampNanos: Long? = null

                audioSampler?.apply {
                    start()
                    setMuted(state.value.isMuted)
                    setVolume(state.value.volume)
                }

                buffer?.apply {

                    launch {
                        startBuffering()
                    }

                    when {
                        decoder?.hasVideo() == true && decoder?.hasAudio() == true -> {
                            launch {
                                while (isActive) {
                                    if (hasFramesToPlay.get()) extractVideoFrame()?.let { frame ->
                                        currentAudioFrameTimestampNanos?.let { currentTimestampNanos ->
                                            (frame.timestampNanos - currentTimestampNanos).nanoseconds.takeIf { it.isPositive() }
                                                ?.let { delay(it) }
                                        }
                                        renderVideo(frame)
                                    }
                                }
                            }
                            launch {
                                while (isActive) {
                                    if (hasFramesToPlay.get()) extractAudioFrame()?.let {
                                        playAudio(it)
                                        currentAudioFrameTimestampNanos = it.timestampNanos
                                    }
                                }
                            }
                        }

                        decoder?.hasVideo() == true -> launch {
                            while (isActive) {
                                if (hasFramesToPlay.get()) extractVideoFrame()?.let { frame ->
                                    firstVideoFrame.value?.timestampNanos?.let { nextTimestampNanos ->
                                        (nextTimestampNanos - frame.timestampNanos).nanoseconds.takeIf { it.isPositive() }
                                            ?.let { delay(it) }
                                    }
                                    renderVideo(frame)
                                }
                            }
                        }

                        decoder?.hasAudio() == true -> launch {
                            while (isActive) {
                                if (hasFramesToPlay.get()) extractAudioFrame()?.let {
                                    playAudio(it)
                                    currentAudioFrameTimestampNanos = it.timestampNanos
                                }
                            }
                        }
                    }

                    while (isActive) {
                        when {
                            decoder?.hasAudio() == true && decoder?.hasVideo() == true -> hasFramesToPlay.set(
                                audioBufferIsNotEmpty() && videoBufferIsNotEmpty()
                            )

                            decoder?.hasAudio() == true -> hasFramesToPlay.set(audioBufferIsNotEmpty())
                            decoder?.hasVideo() == true -> hasFramesToPlay.set(videoBufferIsNotEmpty())
                        }
                        if (hasFramesToPlay.get()) {
                            if (status.value != PlaybackStatus.PLAYING) setStatus(PlaybackStatus.PLAYING)
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
                            } else setStatus(PlaybackStatus.BUFFERING)
                        }

                        delay((1_000L / media.frameRate).milliseconds)
                    }
                }
            }
        }

        private suspend fun stopPlayback() {
            audioSampler?.stop()

            playbackJob?.cancelAndJoin().also { println("Playback job has been cancelled") }

            buffer?.flush()
        }

        init {
            events.onEach(::println).onEach { event ->
                if (event !in arrayOf(PlayerEvent.Play, PlayerEvent.Pause)) pausedTimestampNanos = null

                eventJob?.cancelAndJoin()
                eventJob = playerScope.launch {
                    when (event) {
                        is PlayerEvent.Load -> {
                            stopPlayback()
                            decoder = Decoder.create(event.settings)
                            decoder?.run {
                                buffer = when {
                                    hasAudio() && hasVideo() -> BufferManager.createAudioVideoBuffer(
                                        this,
                                        audioBufferCapacity = audioBufferCapacity,
                                        videoBufferCapacity = videoBufferCapacity
                                    )

                                    hasAudio() -> BufferManager.createAudioOnlyBuffer(
                                        this,
                                        audioBufferCapacity = audioBufferCapacity
                                    )

                                    hasVideo() -> BufferManager.createVideoOnlyBuffer(
                                        this,
                                        videoBufferCapacity = videoBufferCapacity
                                    )

                                    else -> throw Exception("Unable to load media")
                                }
                                audioSampler = AudioSampler.create(media.audioFormat)
                                setMedia(media.also(::println))
                                setStatus(PlaybackStatus.STOPPED)
                            }
                        }

                        is PlayerEvent.Play -> {
                            pausedTimestampNanos?.let { timestampNanos ->
                                sendEvent(PlayerEvent.SeekTo(timestampNanos.nanoseconds.inWholeMilliseconds))
                                pausedTimestampNanos = null
                            } ?: startPlayback()
                        }

                        is PlayerEvent.Pause -> {
                            stopPlayback()
                            pausedTimestampNanos =
                                state.value.playbackTimestampMillis.milliseconds.inWholeNanoseconds.let { playbackTimestampNanos ->
                                    minOf(
                                        buffer?.firstVideoFrame?.value?.timestampNanos ?: playbackTimestampNanos,
                                        buffer?.firstAudioFrame?.value?.timestampNanos ?: playbackTimestampNanos
                                    )
                                }
                            setStatus(PlaybackStatus.PAUSED)
                        }

                        is PlayerEvent.Stop -> {
                            stopPlayback()
                            decoder?.stop()
                            setStatus(PlaybackStatus.STOPPED)
                            updateState(
                                state.value.copy(
                                    bufferTimestampMillis = 0L,
                                    playbackTimestampMillis = 0L
                                )
                            )
                            setPixels(null)
                        }

                        is PlayerEvent.Complete -> {
                            decoder?.stop()
                            updateState(
                                state.value.copy(
                                    bufferTimestampMillis = 0L,
                                    playbackTimestampMillis = 0L
                                )
                            )
                            setStatus(PlaybackStatus.STOPPED)
                            stopPlayback()
                        }

                        is PlayerEvent.SeekTo -> {
                            stopPlayback()
                            val timestampToSeekMicros = event.timestampMillis.milliseconds.inWholeNanoseconds.coerceIn(
                                0L, media.value?.durationNanos ?: 0L
                            ).nanoseconds.inWholeMicroseconds
                            decoder?.seekTo(timestampToSeekMicros)
                            setStatus(PlaybackStatus.SEEKING)
                            startPlayback()
                        }
                    }
                }
            }.launchIn(playerScope)
        }
    }
}