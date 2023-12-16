package audio

import extension.suspend
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.sound.sampled.BooleanControl
import javax.sound.sampled.FloatControl
import javax.sound.sampled.SourceDataLine
import kotlin.math.log10

internal class DefaultAudioSampler(
    private val sourceDataLine: SourceDataLine,
) : AudioSampler {

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
    }.suspend()

    override suspend fun play(bytes: ByteArray) = playbackMutex.withLock {
        runCatching {
            sourceDataLine.write(bytes, 0, bytes.size)
            Unit
        }
    }.suspend()

    override suspend fun pause() = playbackMutex.withLock {
        runCatching {
            sourceDataLine.stop()
        }
    }.suspend()

    override suspend fun stop() = playbackMutex.withLock {
        runCatching {
            sourceDataLine.flush()
            sourceDataLine.stop()
        }
    }.suspend()

    override fun close() = sourceDataLine.close()
}