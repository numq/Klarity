package controller

import buffer.AudioBufferFactory
import buffer.Buffer
import buffer.VideoBufferFactory
import decoder.AudioDecoderFactory
import decoder.Decoder
import decoder.ProbeDecoderFactory
import decoder.VideoDecoderFactory
import factory.Factory
import factory.SuspendFactory
import frame.Frame
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

class PlayerControllerFactory : Factory<PlayerControllerFactory.Parameters, PlayerController> {
    data class Parameters(
        val initialSettings: PlayerSettings?,
        val probeDecoderFactory: SuspendFactory<ProbeDecoderFactory.Parameters, Decoder<Media, Frame.Probe>>,
        val audioDecoderFactory: SuspendFactory<AudioDecoderFactory.Parameters, Decoder<Media.Audio, Frame.Audio>>,
        val videoDecoderFactory: SuspendFactory<VideoDecoderFactory.Parameters, Decoder<Media.Video, Frame.Video>>,
        val audioBufferFactory: Factory<AudioBufferFactory.Parameters, Buffer<Frame.Audio>>,
        val videoBufferFactory: Factory<VideoBufferFactory.Parameters, Buffer<Frame.Video>>,
        val bufferLoopFactory: Factory<BufferLoopFactory.Parameters, BufferLoop>,
        val playbackLoopFactory: Factory<PlaybackLoopFactory.Parameters, PlaybackLoop>,
        val samplerFactory: SuspendFactory<SamplerFactory.Parameters, Sampler>,
        val rendererFactory: Factory<RendererFactory.Parameters, Renderer>,
    )

    override fun create(parameters: Parameters) = with(parameters) {
        PlayerController.create(
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