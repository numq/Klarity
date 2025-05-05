package com.github.numq.klarity.core.player

import com.github.numq.klarity.core.buffer.BufferFactory
import com.github.numq.klarity.core.controller.PlayerControllerFactory
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.event.PlayerEvent
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.pool.PoolFactory
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.SamplerFactory
import com.github.numq.klarity.core.settings.PlayerSettings
import com.github.numq.klarity.core.state.PlayerState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import kotlin.time.Duration

/**
 * Interface representing a media player.
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
     * A flow that provides the current timestamp of the buffer.
     */
    val bufferTimestamp: StateFlow<Duration>

    /**
     * A flow that provides the current timestamp of the playback.
     */
    val playbackTimestamp: StateFlow<Duration>

    /**
     * A flow that emits events related to the player's state and actions.
     */
    val events: SharedFlow<PlayerEvent>

    /**
     * Attaches the renderer to the player.
     *
     * @param renderer The new renderer to attach.
     */
    fun attachRenderer(renderer: Renderer)

    /**
     * Detaches the renderer from the player.
     */
    fun detachRenderer()

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
     * @property audioBufferSize If the size is less than or equal to zero, it disables audio, otherwise it sets the audio buffer size in frames.
     * @property videoBufferSize If the size is less than or equal to zero, it disables audio, otherwise it sets the video buffer size in frames.
     * @property hardwareAccelerationCandidates Hardware acceleration candidates.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun prepare(
        location: String,
        audioBufferSize: Int = MIN_AUDIO_BUFFER_SIZE,
        videoBufferSize: Int = MIN_VIDEO_BUFFER_SIZE,
        hardwareAccelerationCandidates: List<HardwareAcceleration>? = null
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
     * @param timestamp The seeking timestamp.
     * @property keyFramesOnly Use less precise but faster keyframe seeking.
     *
     * @return A [Result] indicating success or failure of the operation.
     */
    suspend fun seekTo(timestamp: Duration, keyFramesOnly: Boolean = false): Result<Unit>

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
        const val MIN_AUDIO_BUFFER_SIZE = 4

        const val MIN_VIDEO_BUFFER_SIZE = 2

        @Volatile
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
         * Loads pre-installed native libraries based on the auto-detected operating system.
         *
         * This method must be called before creating an instance.
         *
         * @return A [Result] containing either a new [KlarityPlayer] instance or an error if creation fails.
         */
        fun load() = runCatching {
            if (isLoaded) {
                return@runCatching
            }

            val osName = System.getProperty("os.name").lowercase()

            val osFolder = when {
                osName.contains("win") -> "windows"

                osName.contains("mac") -> "macos"

                osName.contains("nux") || osName.contains("nix") -> "linux"

                else -> error("Unsupported OS: $osName")
            }

            val libs = listOf(
                "avutil-59",
                "postproc-58",
                "swresample-5",
                "swscale-8",
                "avcodec-61",
                "avformat-61",
                "avfilter-10",
                "avdevice-61",
                "portaudio",
                "klarity"
            )

            val tmpDir = Files.createTempDirectory("binaries").toFile().apply { deleteOnExit() }

            libs.map { lib ->
                val ext = when (osFolder) {
                    "windows" -> ".dll"

                    "linux" -> ".so"

                    "macos" -> ".dylib"

                    else -> ""
                }

                val libName = "$lib$ext"

                val resPath = "/bin/$osFolder/$libName"

                val tmpFile = File(tmpDir, libName)

                KlarityPlayer::class.java.getResourceAsStream(resPath)?.use { input ->
                    Files.copy(input, tmpFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } ?: error("Library not found in resources: $resPath")

                tmpFile.absolutePath
            }.let { paths ->
                load(
                    avutil = paths[0],
                    postproc = paths[1],
                    swresample = paths[2],
                    swscale = paths[3],
                    avcodec = paths[4],
                    avformat = paths[5],
                    avfilter = paths[6],
                    avdevice = paths[7],
                    portaudio = paths[8],
                    klarity = paths[9],
                )
            }
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
                    audioDecoderFactory = AudioDecoderFactory(),
                    videoDecoderFactory = VideoDecoderFactory(),
                    poolFactory = PoolFactory(),
                    bufferFactory = BufferFactory(),
                    bufferLoopFactory = BufferLoopFactory(),
                    playbackLoopFactory = PlaybackLoopFactory(),
                    samplerFactory = SamplerFactory()
                )
            ).mapCatching(::DefaultKlarityPlayer).getOrThrow()
        }
    }
}