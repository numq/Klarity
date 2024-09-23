package controller

import buffer.AudioBufferFactory
import buffer.VideoBufferFactory
import command.Command
import decoder.AudioDecoderFactory
import decoder.ProbeDecoderFactory
import decoder.VideoDecoderFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.test.runTest
import library.Klarity
import loop.buffer.BufferLoopFactory
import loop.playback.PlaybackLoopFactory
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import renderer.RendererFactory
import sampler.SamplerFactory
import settings.PlayerSettings
import state.PlayerState
import java.io.File
import java.net.URL
import kotlin.time.Duration.Companion.microseconds

class PlayerControllerTest {
    init {
        File(ClassLoader.getSystemResources("bin/decoder").nextElement().let(URL::getFile)).listFiles()?.run {
            Klarity.loadDecoder(
                ffmpegPath = find { file -> file.path.endsWith("ffmpeg") }!!.path,
                klarityPath = find { file -> file.path.endsWith("klarity") }!!.path,
                jniPath = find { file -> file.path.endsWith("jni") }!!.path
            ).getOrThrow()
        }
        File(ClassLoader.getSystemResources("bin/sampler").nextElement().let(URL::getFile)).listFiles()?.run {
            Klarity.loadSampler(
                portAudioPath = find { file -> file.path.endsWith("portaudio") }!!.path,
                klarityPath = find { file -> file.path.endsWith("klarity") }!!.path,
                jniPath = find { file -> file.path.endsWith("jni") }!!.path
            ).getOrThrow()
        }
    }

    private val files = File(ClassLoader.getSystemResources("files").nextElement().let(URL::getFile)).listFiles()

    private val file = files?.find { file -> file.nameWithoutExtension == "audio_video" }!!

    private val location = file.absolutePath

    private lateinit var controller: PlayerController

    @BeforeEach
    fun beforeEach() {
        controller = PlayerControllerFactory().create(
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
        ).getOrThrow()
    }

    @AfterEach
    fun afterEach() {
        controller.close()
    }

    @Test
    fun `handle command execution`() = runTest {
        val actualStates = mutableListOf<PlayerState>()

        controller.state.onEach(actualStates::add).launchIn(backgroundScope)

        controller.execute(Command.Prepare(location, 1, 1))

        val preparedState = controller.state.first()

        assertInstanceOf(PlayerState.Ready.Stopped::class.java, preparedState)

        val media = (controller.state.value as PlayerState.Ready.Stopped).media

        val expectedStates = listOf(
            PlayerState.Empty,
            PlayerState.Ready.Stopped(media = media),
            PlayerState.Ready.Playing(media = media),
            PlayerState.Ready.Paused(media = media),
            PlayerState.Ready.Playing(media = media),
            PlayerState.Ready.Stopped(media = media),
            PlayerState.Ready.Seeking(media = media),
            PlayerState.Ready.Paused(media = media),
            PlayerState.Empty
        )

        val commands = listOf(
            Command.Play,
            Command.Pause,
            Command.Resume,
            Command.Stop,
            Command.SeekTo((0L..media.durationMicros.microseconds.inWholeMilliseconds).random()),
            Command.Release
        )

        commands.forEach { command ->
            controller.execute(command)
        }

        expectedStates.zip(actualStates).forEach { (expectedState, actualState) ->
            assertEquals(expectedState, actualState)
        }
    }

    @Test
    fun `change settings`() = runTest {
        val newSettings = PlayerSettings(
            playbackSpeedFactor = 2f,
            isMuted = true,
            volume = .1f,
            audioBufferSize = 10,
            videoBufferSize = 20,
            seekOnlyKeyframes = true
        )

        controller.changeSettings(newSettings)

        assertEquals(newSettings, controller.settings.value)
    }
}