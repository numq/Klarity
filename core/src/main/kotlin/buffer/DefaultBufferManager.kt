package buffer

import decoder.Decoder
import frame.DecodedFrame
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock
import kotlin.math.ceil

internal class DefaultBufferManager(private val decoder: Decoder) : BufferManager {

    private val lock = ReentrantLock()

    override var bufferDurationMillis = BufferManager.DEFAULT_BUFFER_DURATION_MILLIS
        private set

    override fun changeDuration(durationMillis: Long?) {
        bufferDurationMillis = durationMillis ?: BufferManager.DEFAULT_BUFFER_DURATION_MILLIS
    }

    override fun audioBufferCapacity() =
        BufferManager.DEFAULT_AUDIO_FRAME_RATE
            .takeIf { decoder.media?.hasAudio() == true }
            ?.times(bufferDurationMillis)
            ?.div(1_000L)
            ?.let(::ceil)
            ?.toInt()
            ?.coerceAtLeast(1) ?: 0

    override fun videoBufferCapacity() =
        decoder.media?.info?.frameRate
            ?.times(bufferDurationMillis)
            ?.div(1_000L)
            ?.let(::ceil)
            ?.toInt()
            ?.coerceAtLeast(1) ?: 0

    private val audioBuffer = LinkedList<DecodedFrame>()

    private val videoBuffer = LinkedList<DecodedFrame>()

    override fun audioBufferSize() = lock.withLock { audioBuffer.size }

    override fun videoBufferSize() = lock.withLock { videoBuffer.size }

    override fun bufferIsEmpty() = lock.withLock {
        (audioBufferCapacity() > 0 && audioBuffer.isEmpty()) && (videoBufferCapacity() > 0 && videoBuffer.isEmpty())
    }

    override fun bufferIsFull() = lock.withLock {
        (audioBufferCapacity() > 0 && audioBuffer.size >= audioBufferCapacity()) || (videoBufferCapacity() > 0 && videoBuffer.size >= videoBufferCapacity())
    }

    override fun firstAudioFrame(): DecodedFrame? = lock.withLock { audioBuffer.peek() }

    override fun firstVideoFrame(): DecodedFrame? = lock.withLock { videoBuffer.peek() }

    override fun extractAudioFrame(): DecodedFrame? = lock.withLock { audioBuffer.poll() }

    override fun extractVideoFrame(): DecodedFrame? = lock.withLock { videoBuffer.poll() }

    override fun startBuffering() = flow {
        while (currentCoroutineContext().isActive) {
            if (audioBuffer.size < audioBufferCapacity() || videoBuffer.size < videoBufferCapacity()) {
                decoder.nextFrame()?.let { frame ->

                    emit(frame.timestampNanos)

                    lock.withLock {
                        when (frame) {
                            is DecodedFrame.Audio -> audioBuffer.add(frame)

                            is DecodedFrame.Video -> videoBuffer.add(frame)

                            is DecodedFrame.End -> {
                                audioBuffer.add(frame)
                                videoBuffer.add(frame)

                                /**
                                 * Buffering has been completed
                                 */

                                return@flow
                            }

                            else -> Unit
                        }
                    }
                }
            }
        }
    }

    override fun flush() = lock.withLock {
        audioBuffer.clear()
        videoBuffer.clear()

        /**
         * Buffer has been flushed
         */
    }
}