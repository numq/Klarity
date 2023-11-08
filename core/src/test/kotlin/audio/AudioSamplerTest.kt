package audio

import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import javax.sound.sampled.AudioFormat
import kotlin.random.Random

class AudioSamplerTest {

    companion object {
        @Test
        fun `valid format instance creation`() {
            val audioFormat = AudioFormat(44100F, 8, 2, true, false)

            assertNotNull(AudioSampler.create(audioFormat))
        }

        @Test
        fun `invalid format null creation`() {
            val audioFormat = AudioFormat(0F, 0, 0, true, false)

            assertEquals(null, AudioSampler.create(audioFormat)?.also(AudioSampler::close))
        }
    }

    private var audioSampler: AudioSampler? = null

    @BeforeEach
    fun beforeEach() {
        val audioFormat = AudioFormat(44100F, 8, 2, true, false)
        audioSampler = AudioSampler.create(audioFormat)
    }

    @AfterEach
    fun afterEach() {
        audioSampler?.close()
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