package audio

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.sound.sampled.*
import kotlin.math.log10

/**
 * Interface representing an audio sampler that allows controlling various audio features.
 * Implementations of this interface should be able to start, stop, play audio, and adjust volume and mute settings.
 */
interface AudioSampler : AutoCloseable {

    /**
     * Sets the muted state of the audio sampler.
     * @param state The desired muted state.
     * @return The current muted state after the operation, or `null` if the action was not completed successfully.
     */
    suspend fun setMuted(state: Boolean): Boolean?

    /**
     * Unmutes and sets the volume of the audio sampler.
     * @param value The desired volume level, in the range [0.0, 1.0].
     * @return The actual volume level after the operation, or `null` if the action was not completed successfully.
     */
    suspend fun setVolume(value: Float): Float?

    /**
     * Starts the audio sampler, allowing it to play audio.
     */
    suspend fun start()

    /**
     * Plays the provided audio data.
     * @param bytes The byte array containing the audio data to be played.
     */
    suspend fun play(bytes: ByteArray)

    /**
     * Stops the audio sampler, halting audio playback.
     */
    suspend fun stop()

    /**
     * Companion object providing a factory method to create an [AudioSampler] instance.
     */
    companion object {
        /**
         * Creates an [AudioSampler] instance with the specified [audioFormat].
         * @param audioFormat The audio format for the sampler.
         * @return An [AudioSampler] instance.
         */
        fun create(audioFormat: AudioFormat): AudioSampler =
            (AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, audioFormat)) as SourceDataLine)
                .apply { open(audioFormat, 8192) }
                .let(AudioSampler::Implementation)
    }

    private class Implementation(private val sourceDataLine: SourceDataLine) : AudioSampler {
        private val mutex = Mutex()

        private val muteControl = if (sourceDataLine.isControlSupported(BooleanControl.Type.MUTE)) {
            sourceDataLine.getControl(BooleanControl.Type.MUTE) as? BooleanControl
        } else null

        private val gainControl = if (sourceDataLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl
        } else null

        override suspend fun setMuted(state: Boolean) = mutex.withLock {
            muteControl?.value = state
            muteControl?.value
        }

        override suspend fun setVolume(value: Float) = mutex.withLock {
            value.takeIf { it in 0.0..1.0 }?.let { volumeValue ->
                gainControl?.run {
                    this.value = (20.0f * log10(volumeValue)).coerceIn(minimum, maximum)
                    muteControl?.value = false
                    value
                }
            }
        }

        override suspend fun start() {
            mutex.withLock {
                sourceDataLine.start()
            }
        }

        override suspend fun play(bytes: ByteArray) {
            mutex.withLock {
                sourceDataLine.write(bytes, 0, bytes.size)
            }
        }

        override suspend fun stop() {
            mutex.withLock {
                sourceDataLine.stop()
                sourceDataLine.flush()
            }
        }

        override fun close() = sourceDataLine.close()
    }
}
