package audio

import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.sound.sampled.*
import kotlin.math.log10

class AudioSampler private constructor(private val sourceDataLine: SourceDataLine) : AutoCloseable {

    private val mutex = Mutex()

    private val muteControl = if (sourceDataLine.isControlSupported(BooleanControl.Type.MUTE)) {
        sourceDataLine.getControl(BooleanControl.Type.MUTE) as? BooleanControl
    } else null

    private val gainControl = if (sourceDataLine.isControlSupported(FloatControl.Type.MASTER_GAIN)) {
        sourceDataLine.getControl(FloatControl.Type.MASTER_GAIN) as? FloatControl
    } else null

    companion object {
        fun create(audioFormat: AudioFormat) = runCatching {
            val info = DataLine.Info(SourceDataLine::class.java, audioFormat)

            val bufferSizeBytes = with(audioFormat) { 0.01 * frameRate * frameSize }.toInt()

            (AudioSystem.getLine(info) as? SourceDataLine)?.apply {
                open(audioFormat, bufferSizeBytes)
            }?.let(::AudioSampler)
        }.onFailure { println(it.localizedMessage) }.getOrNull()
    }

    suspend fun setMuted(state: Boolean) = mutex.withLock {
        muteControl?.value = state
        muteControl?.value ?: false
    }

    suspend fun setVolume(value: Float) = mutex.withLock {
        gainControl?.run {
            if (value in 0.0..1.0) {

                this.value = (20.0f * log10(value)).coerceIn(minimum, maximum)

                return@withLock value
            }
            null
        }
    }

    suspend fun start() = mutex.withLock {
        sourceDataLine.start()
    }

    suspend fun play(bytes: ByteArray) = mutex.withLock {
        sourceDataLine.write(bytes, 0, bytes.size)
    }

    suspend fun stop() = mutex.withLock {
        sourceDataLine.stop()
        sourceDataLine.flush()
    }

    override fun close() = sourceDataLine.close()
}
