package playback

import io.github.numq.klarity.player.KlarityPlayer
import io.github.numq.klarity.probe.ProbeManager
import io.github.numq.klarity.state.PlayerState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import renderer.RendererRegistry
import kotlin.time.Duration

interface PlaybackService {
    val state: StateFlow<PlaybackState>

    suspend fun getDuration(location: String): Result<Duration?>

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
        private val rendererRegistry: RendererRegistry,
    ) : PlaybackService {
        private val coroutineScope = CoroutineScope(Dispatchers.Default)

        override val state = combine(
            player.state,
            player.bufferTimestamp.map { timestamp -> timestamp.coerceAtLeast(Duration.ZERO) },
            player.playbackTimestamp.map { timestamp -> timestamp.coerceAtLeast(Duration.ZERO) },
            player.settings
        ) { state, bufferTimestamp, playbackTimestamp, settings ->
            when (state) {
                is PlayerState.Empty -> PlaybackState.Empty

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
            }
        }.stateIn(scope = coroutineScope, started = SharingStarted.Eagerly, initialValue = PlaybackState.Empty)

        override suspend fun getDuration(location: String) = runCatching {
            probeManager.probe(location = location).getOrNull()?.duration
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

        override suspend fun seekTo(timestamp: Duration) = player.seekTo(timestamp = timestamp)

        override suspend fun release() = player.release()

        override suspend fun close() = runCatching {
            coroutineScope.cancel()

            player.close().getOrThrow()
        }
    }
}