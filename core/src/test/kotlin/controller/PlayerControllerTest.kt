package controller

import JNITest
import com.github.numq.klarity.core.buffer.AudioBufferFactory
import com.github.numq.klarity.core.buffer.VideoBufferFactory
import com.github.numq.klarity.core.command.Command
import com.github.numq.klarity.core.controller.PlayerController
import com.github.numq.klarity.core.controller.PlayerControllerFactory
import com.github.numq.klarity.core.decoder.AudioDecoderFactory
import com.github.numq.klarity.core.decoder.ProbeDecoderFactory
import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.loop.buffer.BufferLoopFactory
import com.github.numq.klarity.core.loop.playback.PlaybackLoopFactory
import com.github.numq.klarity.core.renderer.RendererFactory
import com.github.numq.klarity.core.sampler.SamplerFactory
import com.github.numq.klarity.core.settings.PlayerSettings
import com.github.numq.klarity.core.state.PlayerState
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import kotlin.time.Duration.Companion.microseconds

class PlayerControllerTest : JNITest() {
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
        val actualStates = flow {
            controller.state.buffer().onEach(::emit).launchIn(backgroundScope)
        }

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

        expectedStates.zip(actualStates.toList()).forEach { (expectedState, actualState) ->
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