package buffer

import decoder.Decoder
import frame.DecodedFrame
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import media.Media
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class DefaultBufferManager(private val decoder: Decoder) : BufferManager {

    private val lock = ReentrantLock()

    private val audioBuffer = LinkedList<DecodedFrame?>()

    private val videoBuffer = LinkedList<DecodedFrame?>()

    private var audioBufferCapacity = 0

    private var videoBufferCapacity = 0

    override fun changeAudioBufferCapacity(value: Int) {
        audioBufferCapacity = value.coerceAtLeast(1)
    }

    override fun changeVideoBufferCapacity(value: Int) {
        videoBufferCapacity = value.coerceAtLeast(1)
    }

    override fun audioBufferCapacity() = decoder.media?.takeIf(Media::hasAudio)?.run { audioBufferCapacity } ?: 0

    override fun videoBufferCapacity() = decoder.media?.takeIf(Media::hasVideo)?.run { videoBufferCapacity } ?: 0

    override fun audioBufferSize() = lock.withLock { audioBuffer.size }

    override fun videoBufferSize() = lock.withLock { videoBuffer.size }

    override fun bufferIsEmpty() = lock.withLock {
        (decoder.media?.takeIf(Media::hasAudio)?.run { audioBuffer.isEmpty() } == true)
                && (decoder.media?.takeIf(Media::hasVideo)?.run { videoBuffer.isEmpty() } == true)
    }

    override fun bufferIsFull() = lock.withLock {
        (decoder.media?.takeIf(Media::hasAudio)?.run { audioBuffer.size == audioBufferCapacity } == true)
                && (decoder.media?.takeIf(Media::hasVideo)?.run { videoBuffer.size == videoBufferCapacity } == true)
    }

    override fun firstAudioFrame(): DecodedFrame? = lock.withLock { audioBuffer.peek() }

    override fun firstVideoFrame(): DecodedFrame? = lock.withLock { videoBuffer.peek() }

    override fun extractAudioFrame(): DecodedFrame? = lock.withLock { audioBuffer.poll() }

    override fun extractVideoFrame(): DecodedFrame? = lock.withLock { videoBuffer.poll() }

    override fun startBuffering() = flow {
        decoder.media?.run {

            if (hasAudio()) require(audioBufferCapacity > 0)

            if (hasVideo()) require(videoBufferCapacity > 0)

            while (currentCoroutineContext().isActive) {
                if (
                    (hasAudio() && audioBuffer.size < audioBufferCapacity)
                    && (hasVideo() && videoBuffer.size < videoBufferCapacity)
                ) {
                    decoder.nextFrame()?.let { frame ->

                        emit(frame.timestampNanos)

                        lock.withLock {
                            when (frame) {
                                is DecodedFrame.Audio -> if (hasAudio()) audioBuffer.add(frame)

                                is DecodedFrame.Video -> if (hasVideo()) videoBuffer.add(frame)

                                is DecodedFrame.End -> {
                                    if (audioBufferCapacity() > 0) audioBuffer.add(frame)
                                    if (videoBufferCapacity() > 0) videoBuffer.add(frame)
                                    return@flow
                                }

                                else -> Unit
                            }
                        }
                    }
                }
            }
        }
    }

    override fun flush() = lock.withLock {
        audioBuffer.clear()
        videoBuffer.clear()
    }
}