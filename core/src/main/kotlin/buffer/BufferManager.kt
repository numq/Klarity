package buffer

import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import player.MediaInfo
import java.util.*
import kotlin.time.Duration.Companion.nanoseconds

interface BufferManager {

    val audioBufferCapacity: Int
    val videoBufferCapacity: Int
    val info: MediaInfo
    val isCompleted: Boolean
    val bufferTimestampMillis: StateFlow<Long>
    val playbackTimestampMillis: StateFlow<Long>

    val firstAudioFrame: StateFlow<BufferedFrame.Samples?>
    val firstVideoFrame: StateFlow<BufferedFrame.Pixels?>

    val lastAudioFrame: StateFlow<BufferedFrame.Samples?>
    val lastVideoFrame: StateFlow<BufferedFrame.Pixels?>

    fun audioBufferIsNotEmpty(): Boolean
    fun videoBufferIsNotEmpty(): Boolean

    fun extractAudioFrame(): BufferedFrame.Samples?
    fun extractVideoFrame(): BufferedFrame.Pixels?

    suspend fun startBuffering()
    fun flush()

    companion object {
        private const val ZERO_CAPACITY_FACTOR = 0
        private const val DEFAULT_CAPACITY_FACTOR = 5

        fun createAudioVideoBuffer(
            decoder: Decoder,
            audioBufferCapacity: Int = (decoder.info.frameRate * DEFAULT_CAPACITY_FACTOR).toInt(),
            videoBufferCapacity: Int = (decoder.info.frameRate * DEFAULT_CAPACITY_FACTOR).toInt(),
        ): BufferManager = Implementation(
            decoder,
            audioBufferCapacity = audioBufferCapacity,
            videoBufferCapacity = videoBufferCapacity
        )

        fun createVideoOnlyBuffer(
            decoder: Decoder,
            videoBufferCapacity: Int = (decoder.info.frameRate * DEFAULT_CAPACITY_FACTOR).toInt(),
        ): BufferManager = Implementation(decoder, audioBufferCapacity = ZERO_CAPACITY_FACTOR, videoBufferCapacity)

        fun createAudioOnlyBuffer(
            decoder: Decoder,
            audioBufferCapacity: Int = (decoder.info.frameRate * DEFAULT_CAPACITY_FACTOR).toInt(),
        ): BufferManager = Implementation(decoder, audioBufferCapacity, videoBufferCapacity = ZERO_CAPACITY_FACTOR)
    }

    class Implementation(
        private val decoder: Decoder,
        override val audioBufferCapacity: Int,
        override val videoBufferCapacity: Int,
    ) : BufferManager {

        /**
         * Implementation
         */

        private val audioBuffer = ArrayDeque<BufferedFrame.Samples>()

        private val videoBuffer =
            if (decoder.hasVideo()) ArrayDeque<BufferedFrame.Pixels>() else null

        private val _bufferTimestampMillis = MutableStateFlow(0L)

        override val bufferTimestampMillis: StateFlow<Long>
            get() = _bufferTimestampMillis.asStateFlow()

        private val _playbackTimestampMillis = MutableStateFlow(0L)

        override val playbackTimestampMillis: StateFlow<Long>
            get() = _playbackTimestampMillis.asStateFlow()

        private val _firstAudioFrame = MutableStateFlow<BufferedFrame.Samples?>(null)
        override val firstAudioFrame: StateFlow<BufferedFrame.Samples?>
            get() = _firstAudioFrame.asStateFlow()

        private val _firstVideoFrame = MutableStateFlow<BufferedFrame.Pixels?>(null)
        override val firstVideoFrame: StateFlow<BufferedFrame.Pixels?>
            get() = _firstVideoFrame.asStateFlow()

        private val _lastAudioFrame = MutableStateFlow<BufferedFrame.Samples?>(null)
        override val lastAudioFrame: StateFlow<BufferedFrame.Samples?>
            get() = _lastAudioFrame.asStateFlow()

        private val _lastVideoFrame = MutableStateFlow<BufferedFrame.Pixels?>(null)
        override val lastVideoFrame: StateFlow<BufferedFrame.Pixels?>
            get() = _lastVideoFrame.asStateFlow()

        override val info: MediaInfo
            get() = decoder.info

        override var isCompleted = false
            private set

        override fun audioBufferIsNotEmpty() = audioBuffer.isNotEmpty()

        override fun videoBufferIsNotEmpty() = videoBuffer?.isNotEmpty() == true

        override fun extractAudioFrame() = audioBuffer.poll()?.also {
            _playbackTimestampMillis.value = it.timestampNanos.nanoseconds.inWholeMilliseconds
            _firstAudioFrame.value = audioBuffer.firstOrNull()
        }

        override fun extractVideoFrame() = videoBuffer?.poll()?.also {
            _playbackTimestampMillis.value = it.timestampNanos.nanoseconds.inWholeMilliseconds
            _firstVideoFrame.value = videoBuffer.firstOrNull()
        }

        override suspend fun startBuffering() {
            CoroutineScope(currentCoroutineContext()).launch {

                decoder.apply {

                    isCompleted = false

                    while (isActive) {

                        val needMoreFrames = if (hasVideo()) {
                            (audioBuffer.size < audioBufferCapacity) || (videoBuffer?.size?.let { it < videoBufferCapacity } == true)
                        } else {
                            audioBuffer.size < audioBufferCapacity
                        }

                        if (needMoreFrames) {
                            nextFrame()?.let { frame ->
                                _bufferTimestampMillis.value = frame.timestampNanos.nanoseconds.inWholeMilliseconds
                                when (frame) {
                                    is DecodedFrame.Audio -> {
                                        isCompleted = false

                                        BufferedFrame.Samples(
                                            frame.timestampNanos, frame.bytes
                                        ).let { audioFrame ->
                                            audioBuffer.add(audioFrame).also { _lastAudioFrame.value = audioFrame }
                                        }
                                    }

                                    is DecodedFrame.Video -> {
                                        isCompleted = false

                                        BufferedFrame.Pixels(
                                            frame.timestampNanos, frame.bytes
                                        ).let { videoFrame ->
                                            videoBuffer?.add(videoFrame).also { _lastVideoFrame.value = videoFrame }
                                        }
                                    }

                                    is DecodedFrame.End -> isCompleted = true
                                }
                            }
                        } else delay(50L)
                    }
                }
            }.invokeOnCompletion { println("Buffering has been completed") }
        }

        override fun flush() {
            audioBuffer.clear()
            videoBuffer?.clear()
            println("Buffer has been flushed")
        }
    }
}
