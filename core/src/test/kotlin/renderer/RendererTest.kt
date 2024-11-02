package renderer

import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.renderer.Renderer
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RendererTest {
    private lateinit var renderer: Renderer

    @BeforeEach
    fun beforeEach() {
        renderer = Renderer.create(100, 100, 30.0, null).getOrThrow()
    }

    @AfterEach
    fun afterEach() {
        runBlocking {
            renderer.reset()
        }
    }

    @Test
    fun `change playback speed`() = runTest {
        assertEquals(1f, renderer.playbackSpeedFactor.value)

        assertTrue(renderer.setPlaybackSpeed(2f).isSuccess)

        assertEquals(2f, renderer.playbackSpeedFactor.value)

        assertTrue(renderer.setPlaybackSpeed(1f).isSuccess)

        assertEquals(1f, renderer.playbackSpeedFactor.value)
    }

    @Test
    fun `draw and reset`() = runTest {
        val frame = Frame.Video.Content(
            timestampMicros = 0L,
            bytes = Random(System.currentTimeMillis()).nextBytes(10),
            width = 100,
            height = 100,
            frameRate = 30.0
        )

        assertTrue(renderer.draw(frame).isSuccess)

        assertEquals(frame, renderer.frame.value)

        assertTrue(renderer.reset().isSuccess)

        assertNull(renderer.frame.value)
    }
}
