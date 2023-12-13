package audio

import extension.suspend
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
     * Pauses the audio sampler.
     */
    suspend fun pause()

    /**
     * Stops the audio sampler, halting audio playback.
     */
    suspend fun stop()

    /**
     * Companion object providing a factory method to create an [AudioSampler] instance.
     */
    companion object {
        private val S16LE = AudioFormat(
            44_100F,
            16,
            2,
            true,
            false
        )

        /**
         * Creates an [AudioSampler] instance.
         * @return An [AudioSampler] instance.
         */
        fun create(bufferSize: Int?): AudioSampler =
            (AudioSystem.getLine(DataLine.Info(SourceDataLine::class.java, S16LE)) as SourceDataLine)
                .apply { open(S16LE, bufferSize ?: 4096) }
                .let(AudioSampler::Implementation)
    }

    private class Implementation(private val sourceDataLine: SourceDataLine) : AudioSampler {

        private val playbackMutex = Mutex()

        private val controlsMutex = Mutex()

        private val muteControl = if (sourceDataLine.isControlSupported(BooleanControl.Type.MUTE)) {
            sourceDataLine.getControl(BooleanControl.Type.MUTE) as? BooleanControl
        } else null

        private val gainControl = if (sourceDataLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
            sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl
        } else null

        override suspend fun setMuted(state: Boolean) = controlsMutex.withLock {
            muteControl?.run {
                value = state
                value
            }
        }

        override suspend fun setVolume(value: Float) = controlsMutex.withLock {
            gainControl?.run {
                value.takeIf { it in 0.0..1.0 }?.let { volumeValue ->
                    this.value = (20.0f * log10(volumeValue)).coerceIn(minimum, maximum)
                    muteControl?.value = false
                    value
                }
            }
        }

        override suspend fun start() = playbackMutex.withLock {
            runCatching {
                sourceDataLine.start()
            }
        }.onFailure(Throwable::printStackTrace).suspend()

        override suspend fun play(bytes: ByteArray) = playbackMutex.withLock {
            runCatching {
                sourceDataLine.write(bytes, 0, bytes.size)
                Unit
            }
        }.onFailure(Throwable::printStackTrace).suspend()

        override suspend fun pause() = playbackMutex.withLock {
            runCatching {
                sourceDataLine.stop()
            }
        }.onFailure(Throwable::printStackTrace).suspend()

        override suspend fun stop() = playbackMutex.withLock {
            runCatching {
                sourceDataLine.flush()
                sourceDataLine.stop()
            }
        }.onFailure(Throwable::printStackTrace).suspend()

        override fun close() = sourceDataLine.close()
    }
}
