package player

import buffer.Buffer
import buffer.BufferFactory
import clock.ClockFactory
import controller.PlayerControllerFactory
import decoder.Decoder
import decoder.DecoderFactory
import event.Event
import factory.Factory
import frame.Frame
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import library.Klarity
import loop.buffer.BufferLoopFactory
import loop.playback.PlaybackLoopFactory
import renderer.Renderer
import renderer.RendererFactory
import sampler.SamplerFactory
import settings.Settings
import state.State

interface KlarityPlayer : AutoCloseable {
    val renderer: StateFlow<Renderer?>
    val settings: StateFlow<Settings>
    val state: StateFlow<State>
    val events: Flow<Event>
    suspend fun changeSettings(settings: Settings)
    suspend fun resetSettings()
    suspend fun load(location: String, enableAudio: Boolean, enableVideo: Boolean)
    suspend fun unload()
    suspend fun play()
    suspend fun pause()
    suspend fun stop()
    suspend fun seekTo(millis: Long)

    @Suppress("UNCHECKED_CAST")
    companion object {
        fun create(): Result<KlarityPlayer> = runCatching {
            check(Klarity.isDecoderLoaded) { "Unable to create player - load native decoder first" }
            check(Klarity.isSamplerLoaded) { "Unable to create player - load native sampler first" }
        }.mapCatching {
            PlayerControllerFactory.create(
                parameters = PlayerControllerFactory.Parameters(
                    defaultSettings = null,
                    clockFactory = ClockFactory,
                    probeDecoderFactory = DecoderFactory as Factory<DecoderFactory.Parameters, Decoder<Nothing>>,
                    audioDecoderFactory = DecoderFactory as Factory<DecoderFactory.Parameters, Decoder<Frame.Audio>>,
                    videoDecoderFactory = DecoderFactory as Factory<DecoderFactory.Parameters, Decoder<Frame.Video>>,
                    audioBufferFactory = BufferFactory as Factory<BufferFactory.Parameters, Buffer<Frame.Audio>>,
                    videoBufferFactory = BufferFactory as Factory<BufferFactory.Parameters, Buffer<Frame.Video>>,
                    bufferLoopFactory = BufferLoopFactory,
                    playbackLoopFactory = PlaybackLoopFactory,
                    samplerFactory = SamplerFactory,
                    rendererFactory = RendererFactory
                )
            ).mapCatching(::DefaultKlarityPlayer).getOrThrow()
        }
    }
}