package jni

import com.github.numq.klarity.core.loader.Klarity
import com.github.numq.klarity.core.sampler.NativeSampler
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.File
import java.net.URL
import kotlin.random.Random

class NativeSamplerTest {
    init {
        File(ClassLoader.getSystemResources("bin/sampler").nextElement().let(URL::getFile)).listFiles()?.run {
            Klarity.loadSampler(
                portAudioPath = find { file -> file.path.endsWith("portaudio") }!!.path,
                klarityPath = find { file -> file.path.endsWith("klarity") }!!.path,
                jniPath = find { file -> file.path.endsWith("jni") }!!.path
            ).getOrThrow()
        }
    }

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
    fun `start and stop playback`() = runTest {
        sampler.start()
        sampler.stop()
    }
}