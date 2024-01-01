package buffer

import frame.DecodedFrame
import media.Media
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

internal class DefaultFrameBuffer(private val media: Media) : FrameBuffer {

    private val lock = ReentrantLock()

    private var audioBufferCapacity = 0

    private var videoBufferCapacity = 0

    private var audioBuffer: LinkedBlockingQueue<DecodedFrame.Audio>? = null

    private var videoBuffer: LinkedBlockingQueue<DecodedFrame.Video>? = null

    override fun changeAudioBufferCapacity(value: Int) {
        audioBuffer = LinkedBlockingQueue(value.coerceAtLeast(1).also { audioBufferCapacity = it })
    }

    override fun changeVideoBufferCapacity(value: Int) {
        videoBuffer = LinkedBlockingQueue(value.coerceAtLeast(1).also { videoBufferCapacity = it })
    }

    override fun audioBufferCapacity() = media.takeIf(Media::hasAudio)?.run { audioBufferCapacity } ?: 0

    override fun videoBufferCapacity() = media.takeIf(Media::hasVideo)?.run { videoBufferCapacity } ?: 0

    override fun audioBufferSize() = lock.withLock { audioBuffer?.size } ?: 0

    override fun videoBufferSize() = lock.withLock { videoBuffer?.size } ?: 0

    override fun bufferIsEmpty() = lock.withLock {
        with(media) {
            (hasAudio() && audioBuffer?.isEmpty() == true)
                    && (hasVideo() && videoBuffer?.isEmpty() == true)
        }
    }

    override fun bufferIsFull() = lock.withLock {
        with(media) {
            (hasAudio() && audioBuffer?.remainingCapacity() == 0)
                    || (hasVideo() && videoBuffer?.remainingCapacity() == 0)
        }
    }

    override fun firstAudioFrame(): DecodedFrame.Audio? = lock.withLock { audioBuffer?.peek() }

    override fun firstVideoFrame(): DecodedFrame.Video? = lock.withLock { videoBuffer?.peek() }

    override fun extractAudioFrame(): DecodedFrame.Audio? = lock.withLock { audioBuffer?.poll() }

    override fun extractVideoFrame(): DecodedFrame.Video? = lock.withLock { videoBuffer?.poll() }

    override fun insertAudioFrame(frame: DecodedFrame.Audio) {
        require(audioBufferCapacity() > 0) {
            "Insertion into an empty buffer is not possible"
        }

        runCatching { audioBuffer?.add(frame) }.getOrDefault(Unit)
    }

    override fun insertVideoFrame(frame: DecodedFrame.Video) {
        require(videoBufferCapacity() > 0) {
            "Insertion into an empty buffer is not possible"
        }

        runCatching { videoBuffer?.add(frame) }.getOrDefault(Unit)
    }

    override fun flush() = lock.withLock {
        audioBuffer?.clear()
        videoBuffer?.clear()
        Unit
    }
}