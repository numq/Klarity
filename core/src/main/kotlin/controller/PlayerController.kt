package controller

import buffer.Buffer
import buffer.BufferFactory
import command.Command
import decoder.Decoder
import decoder.DecoderFactory
import event.Event
import factory.Factory
import frame.Frame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import loop.buffer.BufferLoop
import loop.buffer.BufferLoopFactory
import loop.playback.PlaybackLoop
import loop.playback.PlaybackLoopFactory
import renderer.Renderer
import renderer.RendererFactory
import sampler.Sampler
import sampler.SamplerFactory
import settings.Settings
import state.State
import timestamp.Timestamp

interface PlayerController : AutoCloseable {
    val settings: StateFlow<Settings>
    val state: StateFlow<State>
    val bufferTimestamp: StateFlow<Timestamp>
    val playbackTimestamp: StateFlow<Timestamp>
    val renderer: StateFlow<Renderer?>
    val events: SharedFlow<Event>
    suspend fun changeSettings(newSettings: Settings)
    suspend fun resetSettings()
    suspend fun execute(command: Command)

    companion object {
        internal fun create(
            initialSettings: Settings?,
            probeDecoderFactory: Factory<DecoderFactory.Parameters, Decoder<Nothing>>,
            audioDecoderFactory: Factory<DecoderFactory.Parameters, Decoder<Frame.Audio>>,
            videoDecoderFactory: Factory<DecoderFactory.Parameters, Decoder<Frame.Video>>,
            audioBufferFactory: Factory<BufferFactory.Parameters, Buffer<Frame.Audio>>,
            videoBufferFactory: Factory<BufferFactory.Parameters, Buffer<Frame.Video>>,
            bufferLoopFactory: Factory<BufferLoopFactory.Parameters, BufferLoop>,
            playbackLoopFactory: Factory<PlaybackLoopFactory.Parameters, PlaybackLoop>,
            samplerFactory: Factory<SamplerFactory.Parameters, Sampler>,
            rendererFactory: Factory<RendererFactory.Parameters, Renderer>,
        ): Result<PlayerController> = runCatching {
            DefaultPlayerController(
                initialSettings = initialSettings,
                probeDecoderFactory = probeDecoderFactory,
                audioDecoderFactory = audioDecoderFactory,
                videoDecoderFactory = videoDecoderFactory,
                audioBufferFactory = audioBufferFactory,
                videoBufferFactory = videoBufferFactory,
                bufferLoopFactory = bufferLoopFactory,
                playbackLoopFactory = playbackLoopFactory,
                samplerFactory = samplerFactory,
                rendererFactory = rendererFactory,
            )
        }
    }
}