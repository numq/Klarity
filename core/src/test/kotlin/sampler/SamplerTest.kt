package sampler

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import kotlin.random.Random

class AudioSamplerTest {
    private lateinit var sampler: Sampler

    private val nativeSampler = mockk<NativeSampler>(relaxUnitFun = true)

    @BeforeEach
    fun beforeEach() {
        clearMocks(nativeSampler)
        sampler = DefaultSampler(nativeSampler)
    }

    @AfterEach
    fun afterEach() {
        sampler.close()
    }

    @Test
    fun `change playback speed`() = runTest {
        every { nativeSampler.setPlaybackSpeed(any()) } answers { true }

        assertEquals(1f, sampler.playbackSpeedFactor)

        assertTrue(sampler.setPlaybackSpeed(2f).isSuccess)

        assertEquals(2f, sampler.playbackSpeedFactor)

        assertTrue(sampler.setPlaybackSpeed(1f).isSuccess)

        assertEquals(1f, sampler.playbackSpeedFactor)

        verify { nativeSampler.setPlaybackSpeed(any()) }
    }

    @Test
    fun `change volume`() = runTest {
        every { nativeSampler.setVolume(any()) } answers { true }

        assertTrue(sampler.setVolume(.5f).isSuccess)

        verify { nativeSampler.setVolume(any()) }
    }

    @Test
    fun `toggle mute`() = runTest {
        every { nativeSampler.setVolume(any()) } answers { true }

        assertTrue(sampler.setMuted(true).isSuccess)

        assertTrue(sampler.setMuted(false).isSuccess)

        verify { nativeSampler.setVolume(any()) }
    }

    @Test
    fun `current time`() = runTest {
        every { nativeSampler.currentTime } answers { 1f }

        assertEquals(1f, sampler.getCurrentTime().getOrThrow())

        verify { nativeSampler.currentTime }
    }

    @Test
    fun `playback interaction`() = runTest {
        every { nativeSampler.play(any(), any()) } answers { true }

        assertTrue(sampler.play(Random(System.currentTimeMillis()).nextBytes(10)).getOrThrow())

        assertTrue(sampler.pause().isSuccess)

        assertTrue(sampler.resume().isSuccess)

        assertTrue(sampler.stop().isSuccess)

        verify { nativeSampler.play(any(), any()) }

        verify { nativeSampler.pause() }

        verify { nativeSampler.resume() }

        verify { nativeSampler.stop() }
    }
}