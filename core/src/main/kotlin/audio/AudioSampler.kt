package audio

import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.SourceDataLine


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
        /**
         * S16LE
         */
        val AUDIO_FORMAT = AudioFormat(
            44_100F, 16, 2, true, false
        )

        /**
         * Creates an [AudioSampler] instance.
         * @return An [AudioSampler] instance.
         */
        fun create(): AudioSampler = (AudioSystem.getLine(
            DataLine.Info(
                SourceDataLine::class.java,
                AUDIO_FORMAT
            )
        ) as SourceDataLine).apply { open(format, 8192) }.let(::DefaultAudioSampler)
    }
}
