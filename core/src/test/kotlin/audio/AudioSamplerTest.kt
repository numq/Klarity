package audio

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.*
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import javax.sound.sampled.AudioFormat
import kotlin.random.Random

class AudioSamplerTest {

    @Test
    fun `valid format instance creation`() {
        val audioFormat = AudioFormat(44100F, 8, 2, true, false)

        AudioSampler.create(audioFormat).use { audioSampler ->
            assertNotNull(audioSampler)
        }
    }

    @Test
    fun `invalid format null creation`() {
        val audioFormat = AudioFormat(0F, 0, 0, true, false)

        assertThrows<Exception> {
            AudioSampler.create(audioFormat).close()
        }
    }

    private val audioFormat = AudioFormat(44100F, 8, 2, true, false)
    private var audioSampler: AudioSampler? = null

    @BeforeEach
    fun beforeEach() {
        audioSampler = AudioSampler.create(audioFormat)
    }

    @AfterEach
    fun afterEach() {
        audioSampler?.close()
        audioSampler = null
    }

    @Test
    fun `playback cycle`() = runTest {
        audioSampler!!.apply {
            assertNotNull(start())

            assertNotNull(play(byteArrayOf()))

            assertNotNull(stop())
        }
    }

    @Test
    fun `mute toggling`() = runTest {
        audioSampler!!.apply {
            assertDoesNotThrow {
                repeat(5) {
                    val newState = arrayOf(true, false).random()
                    assertEquals(newState, setMuted(newState))
                }
            }
        }
    }

    @Test
    fun `volume changing`() = runTest {
        audioSampler!!.apply {

            assertEquals(null, setVolume(-1.0f))

            repeat(10) {
                val newVolume = Random.nextDouble(0.0, 1.0).toFloat()
                assertEquals(
                    newVolume,
                    setVolume(newVolume)
                )
            }
        }
    }
}