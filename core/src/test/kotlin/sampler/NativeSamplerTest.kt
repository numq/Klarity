package sampler

import JNITest
import com.github.numq.klarity.core.sampler.NativeSampler
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class NativeSamplerTest : JNITest() {
    private lateinit var sampler: NativeSampler

    @BeforeEach
    fun beforeEach() {
        sampler = NativeSampler(44100, 2)
    }

    @AfterEach
    fun afterEach() {
        sampler.close()
    }

    @Test
    fun `should create and close`() {
        assertDoesNotThrow {
            NativeSampler(sampleRate = 48_000, channels = 2).close()
        }
    }

    @Test
    fun `change playbackSpeed`() {
        sampler.start()
        sampler.setPlaybackSpeed(2f)
        sampler.stop()
    }

    @Test
    fun `change volume`() {
        sampler.start()
        sampler.setVolume(.5f)
        sampler.stop()
    }

    @Test
    fun `play bytes`() {
        sampler.start()
        val bytes = Random(System.currentTimeMillis()).nextBytes(10)
        sampler.play(bytes, bytes.size)
        sampler.stop()
    }

    @Test
    fun `start and stop playback`() {
        sampler.start()
        sampler.stop()
    }
}