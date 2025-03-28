package sampler

import JNITest
import com.github.numq.klarity.core.sampler.Sampler
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class SamplerTest : JNITest() {
    private lateinit var sampler: Sampler

    @BeforeEach
    fun beforeEach() {
        sampler = Sampler.create(44100, 2).getOrThrow()
    }

    @AfterEach
    fun afterEach() = runBlocking {
        sampler.close()
    }

    @Test
    fun `change playback speed`() = runTest {
        assertEquals(1f, sampler.playbackSpeedFactor.value)

        assertTrue(sampler.setPlaybackSpeed(2f).isSuccess)

        assertEquals(2f, sampler.playbackSpeedFactor.value)

        assertTrue(sampler.setPlaybackSpeed(1f).isSuccess)

        assertEquals(1f, sampler.playbackSpeedFactor.value)
    }

    @Test
    fun `change volume`() = runTest {
        assertTrue(sampler.setVolume(.5f).isSuccess)
    }

    @Test
    fun `toggle mute`() = runTest {
        assertTrue(sampler.setMuted(true).isSuccess)

        assertTrue(sampler.setMuted(false).isSuccess)
    }

    @Test
    fun `play bytes and stop`() = runTest {
        assertTrue(sampler.start().isSuccess)

        assertTrue(sampler.play(Random(System.currentTimeMillis()).nextBytes(10)).isSuccess)

        assertTrue(sampler.stop().isSuccess)
    }
}