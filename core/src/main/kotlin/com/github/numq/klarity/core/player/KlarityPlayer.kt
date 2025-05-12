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
import java.net.JarURLConnection
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
         * @param swresample The path to the `swresample-5` binary.
         * @param swscale The path to the `swscale-8` binary.
         * @param avcodec The path to the `avcodec-61` binary.
         * @param avformat The path to the `avformat-61` binary.
         * @param portaudio The path to the `portaudio` binary.
         * @param klarity The path to the `klarity` binary.
         *
         * @return A [Result] containing either a new [KlarityPlayer] instance or an error if creation fails.
         */
        fun load(
            avutil: String,
            swscale: String,
            swresample: String,
            avcodec: String,
            avformat: String,
            portaudio: String,
            klarity: String,
        ) = runCatching {
            System.load(avutil)
            System.load(swresample)
            System.load(swscale)
            System.load(avcodec)
            System.load(avformat)
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

            val extension = when {
                osName.contains("win") -> ".dll"

                osName.contains("mac") -> ".dylib"

                osName.contains("nux") || osName.contains("nix") -> ".so"

                else -> error("Unsupported OS: $osName")
            }

            val libraryNames = listOf(
                "avutil", "swresample", "swscale", "avcodec", "avformat", "portaudio", "klarity"
            )

            val tempDir = Files.createTempDirectory("binaries").toFile().apply { deleteOnExit() }

            val paths = libraryNames.map { libraryName ->
                val resourceDir = "/bin/"
                val resourceUrl = KlarityPlayer::class.java.getResource(resourceDir)
                    ?: error("Resource directory not found: $resourceDir")

                val matchingFile = when (resourceUrl.protocol) {
                    "file" -> {
                        File(resourceUrl.toURI()).listFiles()?.firstOrNull { file ->
                                file.name.contains(libraryName) && file.name.endsWith(extension)
                            }?.name
                    }

                    "jar" -> {
                        (resourceUrl.openConnection() as JarURLConnection).jarFile.entries().iterator().asSequence()
                            .filter { !it.isDirectory }.map { it.name.substringAfterLast('/') }
                            .firstOrNull { fileName ->
                                fileName.contains(libraryName) && fileName.endsWith(extension)
                            }
                    }

                    else -> error("Unsupported resource protocol: ${resourceUrl.protocol}")
                } ?: error("No matching library found for $libraryName (extension: $extension)")

                val tempFile = File(tempDir, matchingFile).apply { deleteOnExit() }

                KlarityPlayer::class.java.getResourceAsStream("$resourceDir$matchingFile")?.use { input ->
                    Files.copy(input, tempFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                } ?: error("Failed to copy library: $matchingFile")

                tempFile.absolutePath
            }

            load(
                avutil = paths[0],
                swresample = paths[1],
                swscale = paths[2],
                avcodec = paths[3],
                avformat = paths[4],
                portaudio = paths[5],
                klarity = paths[6]
            )
        }

        /**
         * Creates a new instance of the KlarityPlayer.
         *
         * @param initialSettings Initial settings of the media player.
         *
         * @return A Result containing either a new KlarityPlayer instance or an error if creation fails.
         */
        fun create(initialSettings: PlayerSettings? = null): Result<KlarityPlayer> = runCatching {
            check(isLoaded) { "Native binaries were not loaded" }

            PlayerControllerFactory().create(
                parameters = PlayerControllerFactory.Parameters(
                    initialSettings = initialSettings,
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