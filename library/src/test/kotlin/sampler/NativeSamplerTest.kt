package sampler

import JNITest
import io.github.numq.klarity.sampler.NativeSampler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class NativeSamplerTest : JNITest() {

    @Test
    fun `should create and close sampler`() = runTest {
        val sampler = NativeSampler(sampleRate = 44100, channels = 2)
        sampler.close()

        assertThrows<IllegalStateException> {
            sampler.setVolume(1.0f).getOrThrow()
        }
    }

    @Test
    fun `should allow setting playback speed and volume`() = runTest {
        val sampler = NativeSampler(sampleRate = 48000, channels = 2)

        val speedResult = sampler.setPlaybackSpeed(1.25f)
        val volumeResult = sampler.setVolume(0.75f)

        assert(speedResult.isSuccess)
        assert(volumeResult.isSuccess)

        sampler.close()
    }

    @Test
    fun `should start write and stop playback`() = runTest {
        val sampler = NativeSampler(sampleRate = 48000, channels = 2)

        val startResult = sampler.start()
        val writeResult = sampler.write(ByteArray(1024))
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
        val drainResult = sampler.drain()

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
