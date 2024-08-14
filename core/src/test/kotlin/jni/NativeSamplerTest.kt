package jni

import kotlinx.coroutines.test.runTest
import library.Klarity
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import sampler.NativeSampler
import java.io.File
import java.net.URL
import kotlin.random.Random

class NativeSamplerTest {
    @Test
    fun `change playbackSpeed`() = runTest {
        sampler.start()
        sampler.setPlaybackSpeed(2f)
        sampler.stop()
    }

    @Test
    fun `change volume`() = runTest {
        sampler.start()
        sampler.setVolume(.5f)
        sampler.stop()
    }

    @Test
    fun `play bytes`() = runTest {
        sampler.start()
        val bytes = Random(System.currentTimeMillis()).nextBytes(10)
        sampler.play(bytes, bytes.size)
        sampler.stop()
    }

    @Test
    fun `pause and resume playback`() = runTest {
        sampler.start()
        sampler.pause()
        sampler.resume()
        sampler.stop()
    }

    @Test
    fun `stop playback`() = runTest {
        sampler.start()
        sampler.stop()
    }

    companion object {
        private val binaries = File(ClassLoader.getSystemResources("bin").nextElement().let(URL::getFile)).listFiles()

        private val samplerBinaries = binaries?.find { file -> file.name == "sampler" }?.listFiles()

        private lateinit var sampler: NativeSampler

        @JvmStatic
        @BeforeAll
        fun beforeAll() {
            checkNotNull(samplerBinaries)
            Klarity.loadSampler(
                portAudioPath = samplerBinaries.first { file -> file.name == "portaudio" }.absolutePath,
                klarityPath = samplerBinaries.first { file -> file.name == "klarity" }.absolutePath,
                jniPath = samplerBinaries.first { file -> file.name == "jni" }.absolutePath
            ).getOrThrow()
            sampler = NativeSampler().apply { initialize(44100, 2) }
        }

        @JvmStatic
        @AfterAll
        fun afterAll() {
            sampler.close()
        }
    }
}