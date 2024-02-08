package controller.player

import controller.stateless.StatelessController
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.*
import media.Media
import playback.PlaybackEvent
import playback.PlaybackState
import playback.PlaybackStatus
import playlist.Playlist
import playlist.PlaylistEvent
import kotlin.time.Duration.Companion.nanoseconds

internal class DefaultPlayerController(
    override val controller: StatelessController,
) : PlayerController {

    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    private val coroutineScope = CoroutineScope(coroutineContext)

    private var mediaJob: Job? = null

    private var playlistJob: Job? = null

    private var playlist: Playlist? = null

    override val renderSink by lazy { controller.renderSink }

    private val _state = MutableStateFlow(PlaybackState())

    override val state = _state.asStateFlow()

    private val _status = MutableStateFlow(PlaybackStatus.EMPTY)

    override val status = _status.asStateFlow()

    private val _exception = Channel<Exception>(onBufferOverflow = BufferOverflow.DROP_OLDEST)

    override val exception: Flow<Exception> = _exception.receiveAsFlow()

    private val handleException: (Throwable) -> Unit = { t ->
        _exception.trySend(if (t is Exception) t else Exception(t))
    }

    private fun handleTimestamps(statelessController: StatelessController) = with(statelessController) {
        merge(bufferTimestampNanos.onEach { bufferTimestampNanos ->
            _state.emit(
                state.value.copy(
                    bufferTimestampMillis = bufferTimestampNanos.nanoseconds.inWholeMilliseconds
                )
            )
        }, playbackTimestampNanos.onEach { playbackTimestampNanos ->
            _state.emit(
                state.value.copy(
                    playbackTimestampMillis = playbackTimestampNanos.nanoseconds.inWholeMilliseconds
                )
            )
        })
    }

    private fun handlePlaylistEvents(playlist: Playlist) = with(playlist) {
        event.onEach { event ->
            when (event) {
                is PlaylistEvent.Add -> Unit

                is PlaylistEvent.Remove -> if (event.removedPlaylistMedia.media == state.value.media) event.nextPlaylistMedia?.let { next ->
                    select(next)
                } ?: unload()

                is PlaylistEvent.Select -> {
                    when (event.playlistMedia.media) {
                        state.value.media -> stop()
                        else -> {
                            if (state.value.media != null) {
                                mediaJob?.cancelAndJoin()
                                mediaJob = null

                                controller.unload()
                            }

                            load(event.playlistMedia.media)

                            play()
                        }
                    }
                }

                is PlaylistEvent.Previous -> {
                    if (state.value.media != null) {
                        mediaJob?.cancelAndJoin()
                        mediaJob = null

                        controller.unload()
                    }

                    event.playlistMedia?.run {
                        load(media)

                        play()
                    }
                }

                is PlaylistEvent.Next -> {
                    if (state.value.media != null) {
                        mediaJob?.cancelAndJoin()
                        mediaJob = null

                        controller.unload()
                    }

                    event.playlistMedia?.run {
                        load(media)

                        play()
                    }
                }
            }
        }
    }

    override suspend fun attachPlaylist(playlist: Playlist) = runCatching {
        playlistJob?.cancel()
        playlistJob = handlePlaylistEvents(playlist.also { this.playlist = it }).launchIn(coroutineScope)
    }.onFailure(handleException).getOrDefault(Unit)

    override suspend fun detachPlaylist() = runCatching {
        playlistJob?.cancelAndJoin()
        playlistJob = null

        playlist = null
    }.onFailure(handleException).getOrDefault(Unit)

    override suspend fun changeRemoteBufferSizeFactor(value: Int) = controller.runCatching {
        changeRemoteBufferSizeFactor(value)
    }.onFailure(handleException).getOrDefault(Unit)

    override suspend fun snapshot(timestampMillis: Long) = controller.runCatching {
        snapshot(timestampMillis)
    }.onFailure(handleException).getOrNull()

    override suspend fun toggleMute() = controller.runCatching {
        setMuted(!state.value.isMuted)?.let { isMuted ->
            _state.emit(state.value.copy(isMuted = isMuted))
        } ?: Unit
    }.onFailure(handleException).getOrDefault(Unit)

    override suspend fun changeVolume(value: Float) = controller.runCatching {
        changeVolume(value)?.let { volume ->
            _state.emit(state.value.copy(volume = volume))
        } ?: Unit
    }.onFailure(handleException).getOrDefault(Unit)

    override suspend fun load(media: Media) = controller.runCatching {
        unload()

        load(media)
    }.onFailure(handleException).getOrDefault(Unit)

    override suspend fun unload() = controller.runCatching {
        unload()

        renderSink.erase()

        Unit
    }.onFailure(handleException).getOrDefault(Unit)

    override suspend fun play() = controller.runCatching {
        play()
    }.onFailure(handleException).getOrDefault(Unit)

    override suspend fun pause() = controller.runCatching {
        pause()
    }.onFailure(handleException).getOrDefault(Unit)

    override suspend fun stop() = controller.runCatching {
        stop()

//        state.value.media?.info?.previewFrame?.let(videoSink::updateVideoFrame)
        snapshot(0L)?.let { renderSink.draw(it) }

        Unit
    }.onFailure(handleException).getOrDefault(Unit)

    override suspend fun seekTo(timestampMillis: Long) = controller.runCatching {
        seekTo(timestampMillis)
    }.onFailure(handleException).getOrDefault(Unit)

    override fun close() {
        coroutineScope.cancel()

        controller.close()
    }

    init {
        controller.event.onEach { event ->
            when (event) {
                is PlaybackEvent.Load -> {
                    mediaJob = handleTimestamps(controller).launchIn(coroutineScope)

                    _state.emit(
                        state.value.copy(
                            media = event.media, bufferTimestampMillis = 0L, playbackTimestampMillis = 0L
                        )
                    )

//                    if (playlist?.hasNext?.value == false) event.media.info.previewFrame?.let(videoSink::updateVideoFrame)
                    if (playlist?.hasNext?.value == false) snapshot(0L)?.let { renderSink.draw(it) }

                    _status.emit(PlaybackStatus.LOADED)
                }

                is PlaybackEvent.Unload -> {
                    mediaJob?.cancelAndJoin()
                    mediaJob = null

                    if (playlist?.hasNext?.value == false) {
                        _state.emit(
                            state.value.copy(
                                media = null, bufferTimestampMillis = 0L, playbackTimestampMillis = 0L
                            )
                        )

                        _status.emit(PlaybackStatus.EMPTY)

                        renderSink.erase()
                    }
                }

                is PlaybackEvent.Play,
                is PlaybackEvent.Resume,
                -> _status.emit(PlaybackStatus.PLAYING)

                is PlaybackEvent.Pause -> _status.emit(PlaybackStatus.PAUSED)

                is PlaybackEvent.Stop -> {
                    _state.emit(
                        state.value.copy(
                            bufferTimestampMillis = 0L, playbackTimestampMillis = 0L
                        )
                    )

                    _status.emit(PlaybackStatus.STOPPED)
                }

                is PlaybackEvent.EndOfMedia -> {
                    _status.emit(PlaybackStatus.COMPLETED)

                    if (playlist?.hasNext?.value == true) playlist?.next()
                }

                is PlaybackEvent.SeekStarted -> _status.emit(PlaybackStatus.SEEKING)

                is PlaybackEvent.SeekEnded -> {
                    val timestampMillis = event.timestampNanos.nanoseconds.inWholeMilliseconds

                    _state.emit(
                        state.value.copy(
                            bufferTimestampMillis = timestampMillis, playbackTimestampMillis = timestampMillis
                        )
                    )
                }
            }
        }.launchIn(coroutineScope)
    }
}