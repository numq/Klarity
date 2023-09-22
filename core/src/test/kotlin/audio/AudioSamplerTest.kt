package audio

import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertDoesNotThrow
import org.junit.jupiter.api.assertThrows
import javax.sound.sampled.AudioFormat

class AudioSamplerTest {

    @Test
    fun `instance creation success`() {
        val audioFormat = AudioFormat(44100F, 8, 2, true, false)
        var audioSampler: AudioSampler?

        assertDoesNotThrow {
            audioSampler = AudioSampler.create(audioFormat)

            assertNotNull(audioSampler)
        }
    }

    @Test
    fun `instance creation fail`() {
        val audioFormat = AudioFormat(0F, 0, 0, true, false)

        assertThrows<Exception> {
            AudioSampler.create(audioFormat)
        }
    }

    @Test
    fun `mute toggling`() {
        val audioFormat = AudioFormat(44100F, 8, 2, true, false)

        AudioSampler.create(audioFormat).use { sampler ->
            sampler.apply {
                start()

                assertDoesNotThrow {
                    setMuted(true)
                    setMuted(false)
                    setMuted(false)
                    setMuted(true)
                }

                stop()
            }
        }
    }

    @Test
    fun `volume changing`() {
        val audioFormat = AudioFormat(44100F, 8, 2, true, false)
        AudioSampler.create(audioFormat).use { sampler ->
            sampler.apply {
                start()

                assertDoesNotThrow {
                    setVolume(.0)
                    setVolume(.5)
                    setVolume(.33333)
                    setVolume(1.0)
                }

                stop()
            }
        }
    }

    @Test
    fun `playback cycle`() {
        val audioFormat = AudioFormat(44100F, 8, 2, true, false)
        AudioSampler.create(audioFormat).use { sampler ->
            sampler.apply {
                start()

                assertDoesNotThrow {
                    play(byteArrayOf())
                }

                stop()
            }
        }
    }
}