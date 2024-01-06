package audio

import java.util.concurrent.locks.ReentrantLock
import javax.sound.sampled.BooleanControl
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.concurrent.withLock
import kotlin.math.log10

internal class DefaultAudioSampler(
    private val sourceDataLine: SourceDataLine,
) : AudioSampler {

    private val lock = ReentrantLock()

    private val muteControl = with(sourceDataLine) {
        takeIf { isControlSupported(BooleanControl.Type.MUTE) }
            ?.getControl(BooleanControl.Type.MUTE) as? BooleanControl
    }

    private val gainControl = with(sourceDataLine) {
        takeIf { isControlSupported(FloatControl.Type.MASTER_GAIN) }
            ?.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl
    }

    override fun setMuted(state: Boolean) = lock.withLock {
        muteControl?.run {
            value = state
            value
        }
    }

    override fun setVolume(value: Float) = lock.withLock {
        gainControl?.run control@{
            value.takeIf { it in 0.0..1.0 }?.let { volumeValue ->
                this@control.value = (20.0f * log10(volumeValue)).coerceIn(minimum, maximum)
                muteControl?.value = false
                value
            }
        }
    }

    override fun start() = lock.withLock {
        sourceDataLine.start()
    }

    override fun play(bytes: ByteArray) = lock.withLock {
        sourceDataLine.write(bytes, 0, bytes.size)

        Unit
    }

    override fun stop() = lock.withLock {
        with(sourceDataLine) {
            stop()
            flush()
        }
    }

    override fun close() = lock.withLock {
        sourceDataLine.close()
    }
}