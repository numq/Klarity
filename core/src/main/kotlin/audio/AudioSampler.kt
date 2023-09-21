package audio

import javax.sound.sampled.*
import kotlin.math.log10

class AudioSampler private constructor(private val sourceDataLine: SourceDataLine) : AutoCloseable {

    private val muteControl = sourceDataLine.getControl(BooleanControl.Type.MUTE) as BooleanControl

    private val volumeControl = sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN) as FloatControl

    companion object {
        private const val S16LE_FRAME_SIZE = 4096

        fun create(audioFormat: AudioFormat): AudioSampler {
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)
            return (AudioSystem.getLine(info) as? SourceDataLine)?.apply {
                open(audioFormat, S16LE_FRAME_SIZE)
            }?.let(::AudioSampler) ?: throw AudioSamplerException.FailedToCreate
        }
    }

    private fun interact(action: () -> Unit) = runCatching { action() }
        .onFailure { println(it.localizedMessage) }
        .getOrNull() ?: throw AudioSamplerException.FailedToInteract(object {}.javaClass.enclosingMethod.name)

    fun setMuted(state: Boolean) = interact {
        muteControl.value = state
    }

    fun setVolume(value: Double) = interact {
        if (value in 0.0..1.0) volumeControl.value = (20.0 * log10(value)).toFloat()
    }

    fun start() = interact {
        sourceDataLine.start()
    }

    fun play(bytes: ByteArray) = interact {
        sourceDataLine.write(bytes, 0, bytes.size)
    }

    fun stop() = interact {
        sourceDataLine.stop()
        sourceDataLine.flush()
    }

    override fun close() = interact {
        sourceDataLine.drain()
        sourceDataLine.close()
    }
}
