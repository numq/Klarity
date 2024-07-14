package jni

import kotlinx.coroutines.test.runTest
import library.Klarity
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import sampler.NativeSampler
import java.io.File
import java.net.URL
import kotlin.random.Random

class NativeSamplerTest {
    private lateinit var sampler: NativeSampler

    companion object {
        private val binaries = File(ClassLoader.getSystemResources("bin").nextElement().let(URL::getFile)).listFiles()

        private val samplerBinaries = binaries?.find { file -> file.name == "sampler" }?.listFiles()

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            checkNotNull(samplerBinaries)
            Klarity.loadSampler(
                openalPath = samplerBinaries.first { file -> file.name == "openal" }.absolutePath,
                klarityPath = samplerBinaries.first { file -> file.name == "klarity" }.absolutePath,
                jniPath = samplerBinaries.first { file -> file.name == "jni" }.absolutePath
            ).getOrThrow()
        }
    }

    @BeforeEach
    fun beforeEach() {
        sampler = NativeSampler().apply { check(init(44100, 2, 4)) }
    }

    @AfterEach
    fun afterEach() {
        sampler.close()
    }

    @Test
    fun `get current time`() = runTest {
        assertEquals(0f, sampler.currentTime)
    }

    @Test
    fun `change playbackSpeed`() = runTest {
        assertTrue(sampler.setPlaybackSpeed(2f))
    }

    @Test
    fun `change volume`() = runTest {
        assertTrue(sampler.setVolume(.5f))
    }

    @Test
    fun `play bytes`() = runTest {
        val bytes = Random(System.currentTimeMillis()).nextBytes(10)
        assertTrue(sampler.play(bytes, bytes.size))
    }

    @Test
    fun `pause and resume playback`() = runTest {
        sampler.pause()
        sampler.resume()
    }

    @Test
    fun `stop playback`() = runTest {
        sampler.stop()
    }
}