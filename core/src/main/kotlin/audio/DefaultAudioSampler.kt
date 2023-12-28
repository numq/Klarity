package audio

import extension.suspend
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

    private val muteControl = if (sourceDataLine.isControlSupported(BooleanControl.Type.MUTE)) {
        sourceDataLine.getControl(BooleanControl.Type.MUTE) as? BooleanControl
    } else null

    private val gainControl = if (sourceDataLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
        sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl
    } else null

    override suspend fun setMuted(state: Boolean) = lock.withLock {
        muteControl?.run {
            value = state
            value
        }
    }

    override suspend fun setVolume(value: Float) = lock.withLock {
        gainControl?.run {
            value.takeIf { it in 0.0..1.0 }?.let { volumeValue ->
                this.value = (20.0f * log10(volumeValue)).coerceIn(minimum, maximum)
                muteControl?.value = false
                value
            }
        }
    }

    override suspend fun start() = lock.withLock {
        runCatching {
            sourceDataLine.flush()
            sourceDataLine.start()
        }
    }.suspend()

    override suspend fun play(bytes: ByteArray) = lock.withLock {
        runCatching {
            sourceDataLine.write(bytes, 0, bytes.size)
            Unit
        }
    }.suspend()

    override suspend fun pause() = lock.withLock {
        runCatching {
            sourceDataLine.stop()
        }
    }.suspend()

    override suspend fun stop() = lock.withLock {
        runCatching {
            sourceDataLine.stop()
            sourceDataLine.flush()
        }
    }.suspend()

    override fun close() = lock.withLock {
        sourceDataLine.close()
    }
}