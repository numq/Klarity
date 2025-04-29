package com.github.numq.klarity.core.controller

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.buffer.BufferFactory
import com.github.numq.klarity.core.command.Command
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.factory.Factory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.loop.buffer.BufferLoop
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoop
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.Sampler
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
            audioDecoderFactory: Factory<AudioDecoderFactory.Parameters, Decoder<Media.Audio>>,
            videoDecoderFactory: Factory<VideoDecoderFactory.Parameters, Decoder<Media.Video>>,
            bufferFactory: Factory<BufferFactory.Parameters, Buffer<Frame>>,
            bufferLoopFactory: Factory<BufferLoopFactory.Parameters, BufferLoop>,
            playbackLoopFactory: Factory<PlaybackLoopFactory.Parameters, PlaybackLoop>,
            samplerFactory: Factory<SamplerFactory.Parameters, Sampler>
        ): Result<PlayerController> = runCatching {
            DefaultPlayerController(
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
}