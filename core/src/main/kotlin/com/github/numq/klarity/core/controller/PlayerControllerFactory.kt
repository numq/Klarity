package com.github.numq.klarity.core.controller

import com.github.numq.klarity.core.buffer.BufferFactory
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.pool.PoolFactory
import com.github.numq.klarity.core.sampler.SamplerFactory
import com.github.numq.klarity.core.settings.PlayerSettings

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