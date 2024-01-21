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
    fun setMuted(state: Boolean): Boolean?

    /**
     * Sets the volume of the audio sampler, unmutes if muted.
     * @param value The desired volume level, in the range [0.0, 1.0].
     * @return The actual volume level after the operation, or `null` if the action was not completed successfully.
     */
    fun setVolume(value: Float): Float?

    /**
     * Starts the audio sampler, allowing it to play audio.
     */
    fun start()

    /**
     * Plays the provided audio data.
     * @param bytes The byte array containing the audio data to be played.
     */
    fun play(bytes: ByteArray)

    /**
     * Stops the audio sampler, halting audio playback. Clears the buffer.
     */
    fun stop()

    /**
     * Companion object providing a factory method to create an [AudioSampler] instance.
     */
    companion object {
        /**
         * Creates an [AudioFormat] with S16LE encoding, 2 channels, and the specified sample rate.
         * @param sampleRate The sample rate as a float number.
         * @param channels The number of audio channels.
         */
        private fun createAudioFormat(sampleRate: Float, channels: Int): AudioFormat =
            AudioFormat(sampleRate, 16, channels, true, false)

        /**
         * Creates an [AudioSampler] instance.
         * @param sampleRate The sample rate as a float number.
         * @param bufferSize The size of the audio buffer or null.
         * @return An [AudioSampler] instance.
         */
        fun create(
            sampleRate: Float,
            channels: Int,
            bufferSize: Int? = null,
        ): AudioSampler = createAudioFormat(sampleRate, channels)
            .run { DataLine.Info(SourceDataLine::class.java, this) }
            .run { AudioSystem.getLine(this) as SourceDataLine }
            .apply { bufferSize?.run { open(format, this) } ?: open(format) }.let(::DefaultAudioSampler)
    }
}
