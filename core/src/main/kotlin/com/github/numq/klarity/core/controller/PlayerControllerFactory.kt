package com.github.numq.klarity.core.controller

import com.github.numq.klarity.core.buffer.AudioBufferFactory
import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.buffer.VideoBufferFactory
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.decoder.ProbeDecoderFactory
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.factory.SuspendFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoop
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.renderer.RendererFactory
import com.github.numq.klarity.core.sampler.Sampler
import com.github.numq.klarity.core.sampler.SamplerFactory
import com.github.numq.klarity.core.settings.PlayerSettings

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