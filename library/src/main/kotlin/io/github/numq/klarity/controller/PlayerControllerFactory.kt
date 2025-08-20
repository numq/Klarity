package io.github.numq.klarity.controller

import io.github.numq.klarity.buffer.BufferFactory
import io.github.numq.klarity.decoder.AudioDecoderFactory
import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.factory.Factory
import io.github.numq.klarity.loop.buffer.BufferLoopFactory
import io.github.numq.klarity.loop.playback.PlaybackLoopFactory
import io.github.numq.klarity.pool.PoolFactory
import io.github.numq.klarity.sampler.SamplerFactory
import io.github.numq.klarity.settings.PlayerSettings

internal class PlayerControllerFactory : Factory<PlayerControllerFactory.Parameters, PlayerController> {
    data class Parameters(
        val initialSettings: PlayerSettings?,
        val audioDecoderFactory: AudioDecoderFactory,
        val videoDecoderFactory: VideoDecoderFactory,
        val poolFactory: PoolFactory,
        val bufferFactory: BufferFactory,
        val bufferLoopFactory: BufferLoopFactory,
        val playbackLoopFactory: PlaybackLoopFactory,
        val samplerFactory: SamplerFactory
    )

    override fun create(parameters: Parameters) = with(parameters) {
        PlayerController.create(
            initialSettings = initialSettings,
            audioDecoderFactory = audioDecoderFactory,
            videoDecoderFactory = videoDecoderFactory,
            poolFactory = poolFactory,
            bufferFactory = bufferFactory,
            bufferLoopFactory = bufferLoopFactory,
            playbackLoopFactory = playbackLoopFactory,
            samplerFactory = samplerFactory
        )
    }
}