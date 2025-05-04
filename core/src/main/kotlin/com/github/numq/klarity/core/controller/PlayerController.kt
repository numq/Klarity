package com.github.numq.klarity.core.controller

import com.github.numq.klarity.core.buffer.BufferFactory
import com.github.numq.klarity.core.command.Command
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.pool.PoolFactory
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.SamplerFactory
import com.github.numq.klarity.core.settings.PlayerSettings
import com.github.numq.klarity.core.state.PlayerState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

interface PlayerController {
    val settings: StateFlow<PlayerSettings>

    val state: StateFlow<PlayerState>

    val bufferTimestamp: StateFlow<Duration>

    val playbackTimestamp: StateFlow<Duration>

    val events: SharedFlow<PlayerEvent>

    fun attachRenderer(renderer: Renderer)

    fun detachRenderer()

    suspend fun changeSettings(newSettings: PlayerSettings): Result<Unit>

    suspend fun resetSettings(): Result<Unit>

    suspend fun execute(command: Command): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        internal fun create(
            initialSettings: PlayerSettings?,
            audioDecoderFactory: AudioDecoderFactory,
            videoDecoderFactory: VideoDecoderFactory,
            poolFactory: PoolFactory,
            bufferFactory: BufferFactory,
            bufferLoopFactory: BufferLoopFactory,
            playbackLoopFactory: PlaybackLoopFactory,
            samplerFactory: SamplerFactory
        ): Result<PlayerController> = runCatching {
            DefaultPlayerController(
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
}