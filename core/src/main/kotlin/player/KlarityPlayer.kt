package player

import buffer.AudioBufferFactory
import buffer.VideoBufferFactory
import controller.PlayerControllerFactory
import decoder.AudioDecoderFactory
import decoder.ProbeDecoderFactory
import decoder.VideoDecoderFactory
import event.PlayerEvent
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import library.Klarity
import loop.buffer.BufferLoopFactory
import loop.playback.PlaybackLoopFactory
import renderer.Renderer
import renderer.RendererFactory
import sampler.SamplerFactory
import settings.PlayerSettings
import state.PlayerState
import timestamp.Timestamp

interface KlarityPlayer : AutoCloseable {
    val settings: StateFlow<PlayerSettings>
    val state: StateFlow<PlayerState>
    val bufferTimestamp: StateFlow<Timestamp>
    val playbackTimestamp: StateFlow<Timestamp>
    val renderer: StateFlow<Renderer?>
    val events: Flow<PlayerEvent>
    suspend fun changeSettings(settings: PlayerSettings)
    suspend fun resetSettings()
    suspend fun prepare(location: String, enableAudio: Boolean, enableVideo: Boolean)
    suspend fun play()
    suspend fun pause()
    suspend fun resume()
    suspend fun stop()
    suspend fun seekTo(millis: Long)
    suspend fun release()

    companion object {
        fun create(): Result<KlarityPlayer> = runCatching {
            check(Klarity.isDecoderLoaded) { "Unable to create player - load native decoder first" }
            check(Klarity.isSamplerLoaded) { "Unable to create player - load native sampler first" }
        }.mapCatching {
            PlayerControllerFactory().create(
                parameters = PlayerControllerFactory.Parameters(
                    initialSettings = null,
                    probeDecoderFactory = ProbeDecoderFactory(),
                    audioDecoderFactory = AudioDecoderFactory(),
                    videoDecoderFactory = VideoDecoderFactory(),
                    audioBufferFactory = AudioBufferFactory(),
                    videoBufferFactory = VideoBufferFactory(),
                    bufferLoopFactory = BufferLoopFactory(),
                    playbackLoopFactory = PlaybackLoopFactory(),
                    samplerFactory = SamplerFactory(),
                    rendererFactory = RendererFactory()
                )
            ).mapCatching(::DefaultKlarityPlayer).getOrThrow()
        }
    }
}