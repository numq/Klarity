package com.github.numq.klarity.core.player

import com.github.numq.klarity.core.buffer.AudioBufferFactory
import com.github.numq.klarity.core.buffer.VideoBufferFactory
import com.github.numq.klarity.core.closeable.SuspendAutoCloseable
import com.github.numq.klarity.core.controller.PlayerControllerFactory
import com.github.numq.klarity.core.decoder.*
import com.github.numq.klarity.core.event.PlayerEvent
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
interface KlarityPlayer : SuspendAutoCloseable {
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
     * @param hardwareAcceleration Specifies the type of hardware acceleration to use for video playback - NONE (default), CUDA, VAAPI, DXVA2, QSV.
     *                             Possible values are:
     *                             - {@link HardwareAcceleration#NONE}: No hardware acceleration.
     *                             - {@link HardwareAcceleration#CUDA}: Use NVIDIA CUDA for hardware acceleration.
     *                             - {@link HardwareAcceleration#VAAPI}: Use VAAPI for hardware acceleration.
     *                             - {@link HardwareAcceleration#DXVA2}: Use DXVA2 for hardware acceleration.
     *                             - {@link HardwareAcceleration#QSV}: Use Intel Quick Sync Video for hardware acceleration.
     */
    suspend fun prepare(
        location: String,
        enableAudio: Boolean,
        enableVideo: Boolean,
        hardwareAcceleration: HardwareAcceleration = HardwareAcceleration.NONE,
    )

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
         * @return A [Result] indicating the success or failure of the operation.
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
         * Retrieves a list of available hardware acceleration methods for video decoding.
         *
         * @return A [Result] containing a list of supported [HardwareAcceleration] types.
         */
        suspend fun getAvailableHardwareAcceleration() = Decoder.getAvailableHardwareAcceleration()

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