package controller

import buffer.Buffer
import buffer.BufferFactory
import decoder.Decoder
import decoder.DecoderFactory
import factory.Factory
import factory.SuspendFactory
import frame.Frame
import loop.buffer.BufferLoop
import loop.buffer.BufferLoopFactory
import loop.playback.PlaybackLoop
import loop.playback.PlaybackLoopFactory
import renderer.Renderer
import renderer.RendererFactory
import sampler.Sampler
import sampler.SamplerFactory
import settings.Settings

object PlayerControllerFactory : Factory<PlayerControllerFactory.Parameters, PlayerController> {
    data class Parameters(
        val initialSettings: Settings?,
        val probeDecoderFactory: SuspendFactory<DecoderFactory.Parameters, Decoder<Unit>>,
        val audioDecoderFactory: SuspendFactory<DecoderFactory.Parameters, Decoder<Frame.Audio>>,
        val videoDecoderFactory: SuspendFactory<DecoderFactory.Parameters, Decoder<Frame.Video>>,
        val audioBufferFactory: Factory<BufferFactory.Parameters, Buffer<Frame.Audio>>,
        val videoBufferFactory: Factory<BufferFactory.Parameters, Buffer<Frame.Video>>,
        val bufferLoopFactory: Factory<BufferLoopFactory.Parameters, BufferLoop>,
        val playbackLoopFactory: Factory<PlaybackLoopFactory.Parameters, PlaybackLoop>,
        val samplerFactory: Factory<SamplerFactory.Parameters, Sampler>,
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