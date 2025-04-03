package com.github.numq.klarity.core.player

import com.github.numq.klarity.core.buffer.AudioBufferFactory
import com.github.numq.klarity.core.buffer.VideoBufferFactory
import com.github.numq.klarity.core.controller.PlayerControllerFactory
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.ProbeDecoderFactory
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.hwaccel.HardwareAccelerationFallback
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.renderer.RendererFactory
import com.github.numq.klarity.core.sampler.SamplerFactory
import com.github.numq.klarity.core.settings.PlayerSettings
import com.github.numq.klarity.core.state.PlayerState
import com.github.numq.klarity.core.timestamp.Timestamp
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Interface representing a media player capable of handling audio and video playback.
 * The player provides various functionalities for controlling playback, changing settings,
 * and monitoring player state and events.
 */
interface KlarityPlayer {
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
    val events: SharedFlow<PlayerEvent>

    /**
     * Changes the settings of the player.
     *
     * @param settings The new settings to apply.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun changeSettings(settings: PlayerSettings): Result<Unit>

    /**
     * Resets the player's settings to their default values.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun resetSettings(): Result<Unit>

    /**
     * Prepares the player for playback of the specified media.
     *
     * @param location The location of the media file to prepare.
     * @param enableAudio Whether to enable audio playback.
     * @param enableVideo Whether to enable video playback.
     * @param hardwareAcceleration Specifies the type of hardware acceleration used for video decoding.
     * @param hardwareAccelerationFallback Specifies the fallback strategy - which acceleration to use in case of failure - of the hardware acceleration used for video decoding.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun prepare(
        location: String,
        enableAudio: Boolean,
        enableVideo: Boolean,
        hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.None,
        hardwareAccelerationFallback: HardwareAccelerationFallback = HardwareAccelerationFallback(),
    ): Result<Unit>

    /**
     * Starts playback of the prepared media.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun play(): Result<Unit>

    /**
     * Pauses the currently playing media.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun pause(): Result<Unit>

    /**
     * Resumes playback of the paused media.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun resume(): Result<Unit>

    /**
     * Stops the playback of the media.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun stop(): Result<Unit>

    /**
     * Seeks to the specified position in the media.
     *
     * @param millis The position in milliseconds to seek to.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun seekTo(millis: Long): Result<Unit>

    /**
     * Releases any resources held by the player.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun release(): Result<Unit>

    /**
     * Closes the player.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun close(): Result<Unit>

    companion object {
        private var isLoaded = false

        /**
         * Loads the native libraries.
         *
         * This method must be called before creating an instance.
         *
         * @param avutil The path to the `avutil-59` binary.
         * @param postproc The path to the `postproc-58` binary.
         * @param swresample The path to the `swresample-5` binary.
         * @param swscale The path to the `swscale-8` binary.
         * @param avcodec The path to the `avcodec-61` binary.
         * @param avformat The path to the `avformat-61` binary.
         * @param avfilter The path to the `avfilter-10` binary.
         * @param avdevice The path to the `avdevice-61` binary.
         * @param portaudio The path to the `portaudio` binary.
         * @param klarity The path to the `klarity` binary.
         *
         * @return A [Result] containing either a new [KlarityPlayer] instance or an error if creation fails.
         */
        fun load(
            avutil: String,
            postproc: String,
            swscale: String,
            swresample: String,
            avcodec: String,
            avformat: String,
            avfilter: String,
            avdevice: String,
            portaudio: String,
            klarity: String,
        ) = runCatching {
            System.load(avutil)
            System.load(postproc)
            System.load(swresample)
            System.load(swscale)
            System.load(avcodec)
            System.load(avformat)
            System.load(avfilter)
            System.load(avdevice)
            System.load(portaudio)
            System.load(klarity)
        }.onSuccess {
            isLoaded = true
        }

        /**
         * Creates a new instance of the KlarityPlayer.
         *
         * @return A Result containing either a new KlarityPlayer instance or an error if creation fails.
         */
        fun create(): Result<KlarityPlayer> = runCatching {
            check(isLoaded) { "Native binaries were not loaded" }

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