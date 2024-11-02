package com.github.numq.klarity.core.controller

import com.github.numq.klarity.core.buffer.AudioBufferFactory
import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.buffer.VideoBufferFactory
import com.github.numq.klarity.core.command.Command
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.decoder.ProbeDecoderFactory
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.event.PlayerEvent
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
import com.github.numq.klarity.core.state.PlayerState
import com.github.numq.klarity.core.timestamp.Timestamp
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

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