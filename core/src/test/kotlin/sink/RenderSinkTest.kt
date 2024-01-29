package sink

import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class RenderSinkTest {
    companion object {
        private lateinit var renderSink: RenderSink
    }

    @BeforeEach
    fun beforeEach() {
        renderSink = RenderSink.create()
    }

    @Test
    fun `static creation`() {
        assertInstanceOf(RenderSink::class.java, RenderSink.create())
    }

    @Test
    fun `update and dispose video frame`() {
        val bytes = "${System.currentTimeMillis()}".toByteArray()

        assertTrue(renderSink.draw(bytes))

        assertEquals(bytes, renderSink.pixels.value)

        assertTrue(renderSink.erase())

        assertNull(renderSink.pixels.value)
    }
}