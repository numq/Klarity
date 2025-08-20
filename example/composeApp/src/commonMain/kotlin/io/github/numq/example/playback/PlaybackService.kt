package io.github.numq.example.playback

import io.github.numq.example.renderer.RendererRegistry
import io.github.numq.klarity.player.KlarityPlayer
import io.github.numq.klarity.probe.ProbeManager
import io.github.numq.klarity.state.PlayerState
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlin.time.Duration
import kotlin.time.Duration.Companion.milliseconds

interface PlaybackService {
    val state: StateFlow<PlaybackState>

    suspend fun getProbe(location: String): Result<PlaybackProbe?>

    suspend fun attachRenderer(id: String): Result<Unit>

    suspend fun detachRenderer(): Result<Unit>

    suspend fun toggleMute(): Result<Unit>

    suspend fun changeVolume(volume: Float): Result<Unit>

    suspend fun decreasePlaybackSpeed(): Result<Unit>

    suspend fun increasePlaybackSpeed(): Result<Unit>

    suspend fun resetPlaybackSpeed(): Result<Unit>

    suspend fun prepare(location: String): Result<Unit>

    suspend fun play(): Result<Unit>

    suspend fun pause(): Result<Unit>

    suspend fun resume(): Result<Unit>

    suspend fun stop(): Result<Unit>

    suspend fun seekTo(timestamp: Duration): Result<Unit>

    suspend fun release(): Result<Unit>

    suspend fun close(): Result<Unit>

    class Implementation(
        private val probeManager: ProbeManager,
        private val player: KlarityPlayer,
        private val rendererRegistry: RendererRegistry
    ) : PlaybackService {
        private val coroutineScope = CoroutineScope(Dispatchers.Default)

        @Volatile
        private var seekJob: Job? = null

        override val state = combine(
            player.settings,
            player.state,
            player.bufferTimestamp.map { timestamp -> timestamp.coerceAtLeast(Duration.ZERO) },
            player.playbackTimestamp.map { timestamp -> timestamp.coerceAtLeast(Duration.ZERO) },
        ) { settings, state, bufferTimestamp, playbackTimestamp ->
            when (state) {
                is PlayerState.Empty -> PlaybackState.Empty

                is PlayerState.Preparing -> PlaybackState.Preparing

                is PlayerState.Releasing -> PlaybackState.Releasing

                is PlayerState.Ready -> {
                    val location = state.media.location

                    val duration = state.media.duration

                    val isMuted = settings.isMuted

                    val volume = settings.volume

                    val playbackSpeedFactor = settings.playbackSpeedFactor

                    when (state) {
                        is PlayerState.Ready.Playing -> PlaybackState.Ready.Playing(
                            location = location,
                            duration = duration,
                            isMuted = isMuted,
                            volume = volume,
                            bufferTimestamp = bufferTimestamp,
                            playbackTimestamp = playbackTimestamp,
                            playbackSpeedFactor = playbackSpeedFactor
                        )

                        is PlayerState.Ready.Paused -> PlaybackState.Ready.Paused(
                            location = location,
                            duration = duration,
                            isMuted = isMuted,
                            volume = volume,
                            bufferTimestamp = bufferTimestamp,
                            playbackTimestamp = playbackTimestamp,
                            playbackSpeedFactor = playbackSpeedFactor
                        )

                        is PlayerState.Ready.Stopped -> PlaybackState.Ready.Stopped(
                            location = location,
                            duration = duration,
                            isMuted = isMuted,
                            volume = volume,
                            bufferTimestamp = bufferTimestamp,
                            playbackTimestamp = playbackTimestamp,
                            playbackSpeedFactor = playbackSpeedFactor
                        )

                        is PlayerState.Ready.Completed -> PlaybackState.Ready.Completed(
                            location = location,
                            duration = duration,
                            isMuted = isMuted,
                            volume = volume,
                            bufferTimestamp = bufferTimestamp,
                            playbackTimestamp = playbackTimestamp,
                            playbackSpeedFactor = playbackSpeedFactor
                        )

                        is PlayerState.Ready.Seeking -> PlaybackState.Ready.Seeking(
                            location = location,
                            duration = duration,
                            isMuted = isMuted,
                            volume = volume,
                            bufferTimestamp = bufferTimestamp,
                            playbackTimestamp = playbackTimestamp,
                            playbackSpeedFactor = playbackSpeedFactor
                        )
                    }
                }

                is PlayerState.Error -> PlaybackState.Error(exception = state.exception)
            }
        }.stateIn(scope = coroutineScope, started = SharingStarted.Eagerly, initialValue = PlaybackState.Empty)

        override suspend fun getProbe(location: String) = runCatching {
            probeManager.probe(location = location).getOrNull()?.run {
                PlaybackProbe(
                    width = videoFormat?.width ?: 0, height = videoFormat?.height ?: 0, duration = duration
                )
            }
        }

        override suspend fun attachRenderer(id: String) = rendererRegistry.get(id = id).mapCatching { renderer ->
            when (renderer) {
                null -> Unit

                else -> player.attachRenderer(renderer = renderer).getOrThrow()
            }
        }

        override suspend fun detachRenderer() = runCatching {
            player.detachRenderer().getOrThrow()

            Unit
        }

        override suspend fun toggleMute() =
            player.changeSettings(settings = player.settings.value.copy(isMuted = !player.settings.value.isMuted))

        override suspend fun changeVolume(volume: Float) =
            player.changeSettings(settings = player.settings.value.copy(volume = volume))

        override suspend fun decreasePlaybackSpeed() =
            player.changeSettings(settings = player.settings.value.copy(playbackSpeedFactor = KlarityPlayer.MIN_PLAYBACK_SPEED_FACTOR))

        override suspend fun increasePlaybackSpeed() =
            player.changeSettings(settings = player.settings.value.copy(playbackSpeedFactor = KlarityPlayer.MAX_PLAYBACK_SPEED_FACTOR))

        override suspend fun resetPlaybackSpeed() =
            player.changeSettings(settings = player.settings.value.copy(playbackSpeedFactor = KlarityPlayer.NORMAL_PLAYBACK_SPEED_FACTOR))

        override suspend fun prepare(location: String) = player.prepare(location = location)

        override suspend fun play() = player.play()

        override suspend fun pause() = player.pause()

        override suspend fun resume() = player.resume()

        override suspend fun stop() = player.stop()

        override suspend fun seekTo(timestamp: Duration) = runCatching {
            seekJob?.cancel()

            seekJob = coroutineScope.launch {
                delay(100.milliseconds)

                player.seekTo(timestamp = timestamp).getOrThrow()

                player.resume().getOrThrow()
            }
        }

        override suspend fun release() = player.release()

        override suspend fun close() = runCatching {
            coroutineScope.cancel()

            player.close().getOrThrow()
        }
    }
}