package sampler

import io.mockk.clearMocks
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test

class AudioSamplerTest {
    private lateinit var sampler: Sampler

    private val nativeSampler: NativeSampler = mockk()

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
    }

    @Test
    fun `change volume`() = runTest {
        every { nativeSampler.setVolume(any()) } answers { true }

        assertTrue(sampler.setVolume(.5f).isSuccess)
    }

    @Test
    fun `toggle mute`() = runTest {
        every { nativeSampler.setVolume(any()) } answers { true }

        assertTrue(sampler.setMuted(true).isSuccess)

        assertTrue(sampler.setMuted(false).isSuccess)
    }
}