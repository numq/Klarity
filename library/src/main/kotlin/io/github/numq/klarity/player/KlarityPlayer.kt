package io.github.numq.klarity.player

import io.github.numq.klarity.buffer.BufferFactory
import io.github.numq.klarity.controller.PlayerControllerFactory
import io.github.numq.klarity.decoder.AudioDecoderFactory
import io.github.numq.klarity.decoder.VideoDecoderFactory
import io.github.numq.klarity.event.PlayerEvent
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.loop.buffer.BufferLoopFactory
import io.github.numq.klarity.loop.playback.PlaybackLoopFactory
import io.github.numq.klarity.pool.PoolFactory
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.renderer.RendererFactory
import io.github.numq.klarity.sampler.SamplerFactory
import io.github.numq.klarity.settings.PlayerSettings
import io.github.numq.klarity.state.PlayerState
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import java.io.File
import java.nio.file.Files
import kotlin.time.Duration

/**
 * Interface representing a media player.
 */
interface KlarityPlayer {
    /**
     * A flow that emits the renderer of the player or null.
     */
    val renderer: StateFlow<Renderer?>

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
     * Changes the settings of the player.
     *
     * @param settings the new settings to apply
     *
     * @return [Result] indicating success
     */
    suspend fun changeSettings(settings: PlayerSettings): Result<Unit>

    /**
     * Resets the player settings to their default values.
     *
     * @return [Result] indicating success
     */
    suspend fun resetSettings(): Result<Unit>

    /**
     * Prepares the player for playback of the specified media.
     *
     * @param location the location of the media file to prepare
     * @param audioBufferSize if the size is less than or equal to zero, it disables audio, otherwise it sets the audio buffer size in frames
     * @param videoBufferSize if the size is less than or equal to zero, it disables audio, otherwise it sets the video buffer size in frames
     * @param hardwareAccelerationCandidates hardware acceleration candidates
     *
     * @return A [Result] indicating success
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
     * @return [Result] indicating success
     */
    suspend fun play(): Result<Unit>

    /**
     * Pauses the currently playing media.
     *
     * @return [Result] indicating success
     */
    suspend fun pause(): Result<Unit>

    /**
     * Resumes playback of the paused media.
     *
     * @return [Result] indicating success
     */
    suspend fun resume(): Result<Unit>

    /**
     * Stops the playback of the media.
     *
     * @return [Result] indicating success
     */
    suspend fun stop(): Result<Unit>

    /**
     * Seeks to the specified position in the media.
     *
     * @param timestamp The seeking timestamp
     * @param keyFramesOnly Use less precise but faster keyframe seeking
     *
     * @return [Result] indicating success
     */
    suspend fun seekTo(timestamp: Duration, keyFramesOnly: Boolean = false): Result<Unit>

    /**
     * Releases any resources held by the player.
     *
     * @return [Result] indicating success
     */
    suspend fun release(): Result<Unit>

    /**
     * Closes the player.
     *
     * @return [Result] indicating success
     */
    suspend fun close(): Result<Unit>

    companion object {
        @Volatile
        private var isLoaded = false

        const val MIN_AUDIO_BUFFER_SIZE = 4

        const val MIN_VIDEO_BUFFER_SIZE = 2

        /**
         * Loads the native libraries.
         *
         * This method must be called before creating an instance.
         *
         * @param klarity The path to the `klarity` binary

         * @return [Result] indicating a success
         */
        fun load(klarity: String) = runCatching {
            System.load(klarity)
        }.onSuccess {
            isLoaded = true
        }

        /**
         * Loads pre-installed native libraries based on the auto-detected operating system.
         *
         * This method must be called before creating an instance.
         *
         * @return [Result] containing a [KlarityPlayer] instance
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

            val resourceDir = "/bin/"

            val resource = KlarityPlayer::class.java.getResource(resourceDir)
                ?: error("Resource directory not found: $resourceDir")

            val tempFile = Files.createTempFile("klarity", extension).toFile().apply { deleteOnExit() }

            val libraryName = "klarity$extension"

            when (resource.protocol) {
                "file" -> {
                    File(resource.toURI()).listFiles()?.firstOrNull { it.name == libraryName }
                        ?.copyTo(tempFile, overwrite = true) ?: error("Library $libraryName not found in $resourceDir")
                }

                "jar" -> {
                    KlarityPlayer::class.java.getResourceAsStream("$resourceDir$libraryName")
                        ?.use { input -> tempFile.outputStream().use { input.copyTo(it) } }
                        ?: error("Library $libraryName not found in JAR")
                }

                else -> error("Unsupported resource protocol: ${resource.protocol}")
            }

            load(klarity = tempFile.absolutePath)
        }

        /**
         * Creates a new instance of the KlarityPlayer.
         *
         * @param initialSettings initial settings of the media player
         *
         * @return [Result] containing a [KlarityPlayer] instance
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
                    samplerFactory = SamplerFactory(),
                    rendererFactory = RendererFactory()
                )
            ).mapCatching(::DefaultKlarityPlayer).getOrThrow()
        }
    }
}