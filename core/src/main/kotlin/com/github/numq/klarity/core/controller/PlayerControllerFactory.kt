package com.github.numq.klarity.core.controller

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.buffer.BufferFactory
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoop
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.sampler.Sampler
import com.github.numq.klarity.core.sampler.SamplerFactory
import com.github.numq.klarity.core.settings.PlayerSettings

internal class PlayerControllerFactory : Factory<PlayerControllerFactory.Parameters, PlayerController> {
    data class Parameters(
        val initialSettings: PlayerSettings?,
        val audioDecoderFactory: Factory<AudioDecoderFactory.Parameters, Decoder<Media.Audio>>,
        val videoDecoderFactory: Factory<VideoDecoderFactory.Parameters, Decoder<Media.Video>>,
        val bufferFactory: Factory<BufferFactory.Parameters, Buffer<Frame>>,
        val bufferLoopFactory: Factory<BufferLoopFactory.Parameters, BufferLoop>,
        val playbackLoopFactory: Factory<PlaybackLoopFactory.Parameters, PlaybackLoop>,
        val samplerFactory: Factory<SamplerFactory.Parameters, Sampler>
    )

    override fun create(parameters: Parameters) = with(parameters) {
        PlayerController.create(
            initialSettings = initialSettings,
            audioDecoderFactory = audioDecoderFactory,
            videoDecoderFactory = videoDecoderFactory,
            bufferFactory = bufferFactory,
            bufferLoopFactory = bufferLoopFactory,
            playbackLoopFactory = playbackLoopFactory,
            samplerFactory = samplerFactory
        )
    }
}