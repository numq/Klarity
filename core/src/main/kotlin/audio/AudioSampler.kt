package audio

import javax.sound.sampled.*

class AudioSampler private constructor(private val sourceDataLine: SourceDataLine) : AutoCloseable {

    private val muteControl = sourceDataLine.getControl(BooleanControl.Type.MUTE) as BooleanControl

    private val volumeControl = sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl

    companion object {
        private const val S16LE_FRAME_SIZE = 4096

        fun create(audioFormat: AudioFormat?) = DataLine.Info(SourceDataLine::class.java, audioFormat).let { info ->
            (AudioSystem.getLine(info) as? SourceDataLine)?.apply {
                open(audioFormat, S16LE_FRAME_SIZE)
            }?.let(::AudioSampler)
        }
    }

    private fun <T> interact(action: () -> T) = runCatching { action() }.onFailure(::println).getOrNull()

    fun isEmpty() = sourceDataLine.run { available() == 0 }

    fun setMuted(state: Boolean) = interact {
        muteControl.value = state
        muteControl.value
    }

    fun setVolume(value: Double) = interact {
        if (value in 0.0..1.0) {
            val min = volumeControl.minimum
            val max = volumeControl.maximum
            val range = max - min
            val volume = min + value * range
            volumeControl.value = volume.toFloat()
            return@interact if (range == 0f) 0.0 else ((volumeControl.value - min) / range).toDouble()
        }
        null
    }

    fun start() = interact {
        sourceDataLine.start()
    }

    fun play(bytes: ByteArray) = interact {
        sourceDataLine.write(bytes, 0, bytes.size)
    }

    fun pause() = interact {
        sourceDataLine.stop()
    }

    fun stop() = interact {
        sourceDataLine.stop()
        sourceDataLine.flush()
    }

    override fun close() {
        sourceDataLine.drain()
        sourceDataLine.close()
    }
}
