package renderer

import frame.Frame
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class RendererTest {
    companion object {
        private lateinit var renderer: Renderer
    }

    @BeforeEach
    fun beforeEach() {
        renderer = Renderer.create(100, 100, null).getOrThrow()
    }

    @Test
    fun `change playback speed`() = runTest {
        assertEquals(1f, renderer.playbackSpeedFactor)

        renderer.setPlaybackSpeed(2f)

        assertEquals(2f, renderer.playbackSpeedFactor)

        renderer.setPlaybackSpeed(1f)

        assertEquals(1f, renderer.playbackSpeedFactor)
    }

    @Test
    fun `draw and reset`() = runTest {
        val frame = Frame.Video.Content(
            timestampMicros = 0L,
            bytes = Random(System.currentTimeMillis()).nextBytes(10),
            width = 100,
            height = 100,
            frameRate = 30
        )

        assertTrue(renderer.draw(frame).isSuccess)

        assertEquals(frame, renderer.frame.value)

        assertTrue(renderer.reset().isSuccess)

        assertNull(renderer.frame.value)
    }
}
