package controller.media

import buffer.Buffer
import buffer.BufferFactory
import clock.Clock
import clock.ClockFactory
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
import state.InternalState

interface PlayerController : AutoCloseable {
    val settings: StateFlow<Settings>
    val internalState: StateFlow<InternalState>
    val events: SharedFlow<Event>
    suspend fun changeSettings(newSettings: Settings)
    suspend fun resetSettings()
    suspend fun prepare(location: String, audioBufferSize: Int, videoBufferSize: Int)
    suspend fun execute(command: Command)
    suspend fun release()

    companion object {
        internal fun create(
            defaultSettings: Settings?,
            clockFactory: Factory<ClockFactory.Parameters, Clock>,
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
                defaultSettings = defaultSettings,
                clockFactory = clockFactory,
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