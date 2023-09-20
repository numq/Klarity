package audio

import javax.sound.sampled.*
import kotlin.math.log10

class AudioSample private constructor(val sourceDataLine: SourceDataLine) : AutoCloseable {

    private val muteControl = sourceDataLine.getControl(BooleanControl.Type.MUTE) as BooleanControl

    private val volumeControl = sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl

    companion object {
        private const val S16LE_FRAME_SIZE = 4096

        fun make(audioFormat: AudioFormat) = runCatching {
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            (AudioSystem.getLine(info) as? SourceDataLine)?.apply {
                open(audioFormat, S16LE_FRAME_SIZE)
            }?.let(::AudioSample)
        }.getOrNull()
    }

    fun setMuted(state: Boolean) {
        try {
            muteControl.value = state
        } catch (e: Exception) {
            println(e.localizedMessage)
        }
    }

    fun setVolume(value: Double) {
        try {
            if (value in 0.0..1.0) {
                volumeControl.value = (20.0 * log10(value)).toFloat()
            } else {
                println("Invalid volume: $value")
            }
        } catch (e: Exception) {
            println(e.localizedMessage)
        }
    }

    fun start() {
        try {
            sourceDataLine.start()
        } catch (e: Exception) {
            println(e.localizedMessage)
        }
    }

    fun play(bytes: ByteArray) {
        try {
            sourceDataLine.write(bytes, 0, bytes.size)
        } catch (e: Exception) {
            println(e.localizedMessage)
        }
    }

    fun stop() {
        try {
            sourceDataLine.stop()
            sourceDataLine.flush()
        } catch (e: Exception) {
            println(e.localizedMessage)
        }
    }

    override fun close() {
        try {
            sourceDataLine.drain()
            sourceDataLine.close()
        } catch (e: Exception) {
            println(e.localizedMessage)
        }
    }
}
