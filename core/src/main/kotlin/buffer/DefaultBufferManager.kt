package buffer

import decoder.Decoder
import frame.DecodedFrame
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*
import kotlin.math.ceil

internal class DefaultBufferManager(private val decoder: Decoder) : BufferManager {

    private val bufferMutex = Mutex()

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

    override suspend fun audioBufferSize() = bufferMutex.withLock { audioBuffer.size }

    override suspend fun videoBufferSize() = bufferMutex.withLock { videoBuffer.size }

    override suspend fun bufferIsEmpty() = bufferMutex.withLock {
        (audioBufferCapacity() > 0 && audioBuffer.isEmpty()) && (videoBufferCapacity() > 0 && videoBuffer.isEmpty())
    }

    override suspend fun bufferIsFull() = bufferMutex.withLock {
        (audioBufferCapacity() > 0 && audioBuffer.size >= audioBufferCapacity()) || (videoBufferCapacity() > 0 && videoBuffer.size >= videoBufferCapacity())
    }

    override suspend fun firstAudioFrame(): DecodedFrame? = bufferMutex.withLock { audioBuffer.peek() }

    override suspend fun firstVideoFrame(): DecodedFrame? = bufferMutex.withLock { videoBuffer.peek() }

    override suspend fun extractAudioFrame(): DecodedFrame? = bufferMutex.withLock { audioBuffer.poll() }

    override suspend fun extractVideoFrame(): DecodedFrame? = bufferMutex.withLock { videoBuffer.poll() }

    override suspend fun startBuffering() = flow {

        println(audioBufferCapacity())
        println(videoBufferCapacity())

        while (currentCoroutineContext().isActive) {
            if (audioBuffer.size < audioBufferCapacity() || videoBuffer.size < videoBufferCapacity()) {
                decoder.nextFrame()?.let { frame ->

                    emit(frame.timestampNanos)

                    bufferMutex.withLock {
                        when (frame) {
                            is DecodedFrame.Audio -> audioBuffer.add(frame)

                            is DecodedFrame.Video -> videoBuffer.add(frame)

                            is DecodedFrame.End -> {
                                audioBuffer.add(frame)
                                videoBuffer.add(frame)

                                /**
                                 * Buffering has been completed
                                 */

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

    override suspend fun flush() = bufferMutex.withLock {
        audioBuffer.clear()
        videoBuffer.clear()

        /**
         * Buffer has been flushed
         */
    }
}