package audio

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.sound.sampled.*
import kotlin.math.log10

interface AudioSampler : AutoCloseable {

    suspend fun setMuted(state: Boolean): Boolean
    suspend fun setVolume(value: Float): Float
    suspend fun start()
    suspend fun play(bytes: ByteArray)
    suspend fun stop()

    companion object {
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
            muteControl?.value ?: false
        }

        override suspend fun setVolume(value: Float) = mutex.withLock {
            gainControl?.run {
                if (value in 0.0..1.0) {

                    this.value = (20.0f * log10(value)).coerceIn(minimum, maximum)

                    return@withLock value
                }
                null
            } ?: value
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
