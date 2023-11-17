package buffer

import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import java.util.*
import kotlin.math.ceil

interface BufferManager {

    val audioBufferCapacity: Int
    val videoBufferCapacity: Int
    fun audioBufferSize(): Int
    fun videoBufferSize(): Int
    fun bufferIsEmpty(): Boolean
    fun bufferIsFull(): Boolean
    suspend fun firstAudioFrame(): DecodedFrame?
    suspend fun firstVideoFrame(): DecodedFrame?
    suspend fun extractAudioFrame(): DecodedFrame?
    suspend fun extractVideoFrame(): DecodedFrame?
    suspend fun startBuffering(): Flow<Long>
    fun flush()

    companion object {
        fun create(
            decoder: Decoder,
            bufferDurationSeconds: Int,
        ) = runCatching {
            Implementation(
                decoder = decoder,
                bufferDurationSeconds = bufferDurationSeconds
            )
        }.onFailure { println(it.localizedMessage) }.getOrNull()
    }

    class Implementation(
        private val decoder: Decoder,
        bufferDurationSeconds: Int,
    ) : BufferManager {

        override val audioBufferCapacity = ceil(bufferDurationSeconds * decoder.media.audioFrameRate).toInt()
            .also { println("audio buffer capacity: $it") }

        override val videoBufferCapacity = ceil(bufferDurationSeconds * decoder.media.videoFrameRate).toInt()
            .also { println("video buffer capacity: $it") }

        private val audioBuffer = LinkedList<DecodedFrame>()

        private val videoBuffer = LinkedList<DecodedFrame>()

        override fun audioBufferSize() = audioBuffer.size

        override fun videoBufferSize() = videoBuffer.size

        override fun bufferIsEmpty() =
            (audioBufferCapacity > 0 && audioBuffer.isEmpty()) && (videoBufferCapacity > 0 && videoBuffer.isEmpty())

        override fun bufferIsFull() =
            (audioBufferCapacity > 0 && audioBuffer.size >= audioBufferCapacity) && (videoBufferCapacity > 0 && videoBuffer.size >= videoBufferCapacity)

        override suspend fun firstAudioFrame(): DecodedFrame? = audioBuffer.peek()

        override suspend fun firstVideoFrame(): DecodedFrame? = videoBuffer.peek()

        override suspend fun extractAudioFrame(): DecodedFrame? = audioBuffer.poll()

        override suspend fun extractVideoFrame(): DecodedFrame? = videoBuffer.poll()

        override suspend fun startBuffering() = flow {
            coroutineScope {
                while (isActive) {
                    if (audioBuffer.size < audioBufferCapacity || videoBuffer.size < videoBufferCapacity) {
                        decoder.nextFrame()?.let { frame ->

                            emit(frame.timestampNanos)

                            when (frame) {
                                is DecodedFrame.Audio -> audioBuffer.add(frame)

                                is DecodedFrame.Video -> videoBuffer.add(frame)

                                is DecodedFrame.End -> {
                                    audioBuffer.add(frame)
                                    videoBuffer.add(frame)
                                    return@coroutineScope println("Buffering has been completed")
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun flush() {
            audioBuffer.clear()
            videoBuffer.clear()

            println("Buffer has been flushed")
        }
    }
}