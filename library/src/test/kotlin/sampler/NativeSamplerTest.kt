package sampler

import JNITest
import io.github.numq.klarity.sampler.NativeSampler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NativeSamplerTest : JNITest() {

    @Test
    fun `should create and close sampler`() = runTest {
        NativeSampler(sampleRate = 44100, channels = 2).close()
    }

    @Test
    fun `should start write and stop playback`() = runTest {
        val sampler = NativeSampler(sampleRate = 48000, channels = 2)

        val startResult = sampler.start()
        val writeResult = sampler.write(ByteArray(1024), 1f, 1f)
        val stopResult = sampler.stop()

        assert(startResult.isSuccess)
        assert(writeResult.isSuccess)
        assert(stopResult.isSuccess)

        sampler.close()
    }

    @Test
    fun `should flush and drain without error`() = runTest {
        val sampler = NativeSampler(sampleRate = 44100, channels = 2)

        val flushResult = sampler.flush()
        val drainResult = sampler.drain(1f, 1f)

        assert(flushResult.isSuccess)
        assert(drainResult.isSuccess)

        sampler.close()
    }

    @Test
    fun `should fail with invalid arguments`() {
        assertThrows<IllegalArgumentException> {
            NativeSampler(sampleRate = 0, channels = 2)
        }

        assertThrows<IllegalArgumentException> {
            NativeSampler(sampleRate = 44100, channels = 0)
        }
    }
}
