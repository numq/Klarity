package io.github.numq.klarity.controller

import io.github.numq.klarity.buffer.BufferFactory
import io.github.numq.klarity.command.Command
import io.github.numq.klarity.decoder.AudioDecoderFactory
import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.event.PlayerEvent
import io.github.numq.klarity.loop.buffer.BufferLoopFactory
import io.github.numq.klarity.loop.playback.PlaybackLoopFactory
import io.github.numq.klarity.pool.PoolFactory
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.sampler.SamplerFactory
import io.github.numq.klarity.settings.PlayerSettings
import io.github.numq.klarity.state.PlayerState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlin.time.Duration

internal interface PlayerController {
    val settings: StateFlow<PlayerSettings>

    val state: StateFlow<PlayerState>

    val bufferTimestamp: StateFlow<Duration>

    val playbackTimestamp: StateFlow<Duration>

    val events: SharedFlow<PlayerEvent>

    suspend fun attachRenderer(renderer: Renderer): Result<Unit>

    suspend fun detachRenderer(): Result<Renderer?>

    suspend fun changeSettings(newSettings: PlayerSettings): Result<Unit>

    suspend fun resetSettings(): Result<Unit>

    suspend fun execute(command: Command): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        const val MIN_PLAYBACK_SPEED_FACTOR = .5F

        const val MAX_PLAYBACK_SPEED_FACTOR = 2F

        const val NORMAL_PLAYBACK_SPEED_FACTOR = 1F

        fun create(
            initialSettings: PlayerSettings?,
            audioDecoderFactory: AudioDecoderFactory,
            videoDecoderFactory: VideoDecoderFactory,
            poolFactory: PoolFactory,
            bufferFactory: BufferFactory,
            bufferLoopFactory: BufferLoopFactory,
            playbackLoopFactory: PlaybackLoopFactory,
            samplerFactory: SamplerFactory,
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