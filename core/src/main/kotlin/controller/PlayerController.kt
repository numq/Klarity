package controller

import buffer.AudioBufferFactory
import buffer.Buffer
import buffer.VideoBufferFactory
import command.Command
import decoder.AudioDecoderFactory
import decoder.Decoder
import decoder.ProbeDecoderFactory
import decoder.VideoDecoderFactory
import event.PlayerEvent
import factory.Factory
import factory.SuspendFactory
import frame.Frame
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import loop.buffer.BufferLoop
import loop.buffer.BufferLoopFactory
import loop.playback.PlaybackLoop
import loop.playback.PlaybackLoopFactory
import media.Media
import renderer.Renderer
import renderer.RendererFactory
import sampler.Sampler
import sampler.SamplerFactory
import settings.PlayerSettings
import state.PlayerState
import timestamp.Timestamp

interface PlayerController : AutoCloseable {
    val settings: StateFlow<PlayerSettings>
    val state: StateFlow<PlayerState>
    val bufferTimestamp: StateFlow<Timestamp>
    val playbackTimestamp: StateFlow<Timestamp>
    val renderer: StateFlow<Renderer?>
    val events: SharedFlow<PlayerEvent>
    suspend fun changeSettings(newSettings: PlayerSettings)
    suspend fun resetSettings()
    suspend fun execute(command: Command)

    companion object {
        internal fun create(
            initialSettings: PlayerSettings?,
            probeDecoderFactory: SuspendFactory<ProbeDecoderFactory.Parameters, Decoder<Media, Frame.Probe>>,
            audioDecoderFactory: SuspendFactory<AudioDecoderFactory.Parameters, Decoder<Media.Audio, Frame.Audio>>,
            videoDecoderFactory: SuspendFactory<VideoDecoderFactory.Parameters, Decoder<Media.Video, Frame.Video>>,
            audioBufferFactory: Factory<AudioBufferFactory.Parameters, Buffer<Frame.Audio>>,
            videoBufferFactory: Factory<VideoBufferFactory.Parameters, Buffer<Frame.Video>>,
            bufferLoopFactory: Factory<BufferLoopFactory.Parameters, BufferLoop>,
            playbackLoopFactory: Factory<PlaybackLoopFactory.Parameters, PlaybackLoop>,
            samplerFactory: SuspendFactory<SamplerFactory.Parameters, Sampler>,
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