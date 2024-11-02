package com.github.numq.klarity.core.player

import com.github.numq.klarity.core.buffer.AudioBufferFactory
import com.github.numq.klarity.core.buffer.VideoBufferFactory
import com.github.numq.klarity.core.controller.PlayerControllerFactory
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.ProbeDecoderFactory
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.loader.Klarity
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.renderer.RendererFactory
import com.github.numq.klarity.core.sampler.SamplerFactory
import com.github.numq.klarity.core.settings.PlayerSettings
import com.github.numq.klarity.core.state.PlayerState
import com.github.numq.klarity.core.timestamp.Timestamp
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface representing a media player capable of handling audio and video playback.
 * The player provides various functionalities for controlling playback, changing settings,
 * and monitoring player state and events.
 */
interface KlarityPlayer : AutoCloseable {

    /**
     * A flow that emits the current settings of the player.
     */
    val settings: StateFlow<PlayerSettings>

    /**
     * A flow that emits the current state of the player.
     */
    val state: StateFlow<PlayerState>

    /**
     * A flow that provides the current timestamp of the audio buffer.
     */
    val bufferTimestamp: StateFlow<Timestamp>

    /**
     * A flow that provides the current timestamp of the playback.
     */
    val playbackTimestamp: StateFlow<Timestamp>

    /**
     * A flow that provides the current renderer being used for video playback.
     * Can be null if no renderer is set.
     */
    val renderer: StateFlow<Renderer?>

    /**
     * A flow that emits events related to the player's state and actions.
     */
    val events: Flow<PlayerEvent>

    /**
     * Changes the settings of the player.
     *
     * @param settings The new settings to apply.
     */
    suspend fun changeSettings(settings: PlayerSettings)

    /**
     * Resets the player's settings to their default values.
     */
    suspend fun resetSettings()

    /**
     * Prepares the player for playback of the specified media.
     *
     * @param location The location of the media file to prepare.
     * @param enableAudio Whether to enable audio playback.
     * @param enableVideo Whether to enable video playback.
     */
    suspend fun prepare(location: String, enableAudio: Boolean, enableVideo: Boolean)

    /**
     * Starts playback of the prepared media.
     */
    suspend fun play()

    /**
     * Pauses the currently playing media.
     */
    suspend fun pause()

    /**
     * Resumes playback of the paused media.
     */
    suspend fun resume()

    /**
     * Stops the playback of the media.
     */
    suspend fun stop()

    /**
     * Seeks to the specified position in the media.
     *
     * @param millis The position in milliseconds to seek to.
     */
    suspend fun seekTo(millis: Long)

    /**
     * Releases any resources held by the player.
     */
    suspend fun release()

    companion object {
        /**
         * Creates a new instance of the KlarityPlayer.
         *
         * @return A Result containing either a new KlarityPlayer instance or an error if creation fails.
         */
        fun create(): Result<KlarityPlayer> = runCatching {
            check(Klarity.isDecoderLoaded) { "Unable to create player - load native decoder first" }
            check(Klarity.isSamplerLoaded) { "Unable to create player - load native sampler first" }
        }.mapCatching {
            PlayerControllerFactory().create(
                parameters = PlayerControllerFactory.Parameters(
                    initialSettings = null,
                    probeDecoderFactory = ProbeDecoderFactory(),
                    audioDecoderFactory = AudioDecoderFactory(),
                    videoDecoderFactory = VideoDecoderFactory(),
                    audioBufferFactory = AudioBufferFactory(),
                    videoBufferFactory = VideoBufferFactory(),
                    bufferLoopFactory = BufferLoopFactory(),
                    playbackLoopFactory = PlaybackLoopFactory(),
                    samplerFactory = SamplerFactory(),
                    rendererFactory = RendererFactory()
                )
            ).mapCatching(::DefaultKlarityPlayer).getOrThrow()
        }
    }
}