package buffer

import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import player.MediaInfo
import java.util.concurrent.LinkedBlockingQueue
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

    fun audioBufferSize(): Int
    fun videoBufferSize(): Int?

    fun audioBufferIsEmpty(): Boolean
    fun videoBufferIsEmpty(): Boolean?

    fun audioBufferIsNotEmpty(): Boolean
    fun videoBufferIsNotEmpty(): Boolean?

    fun extractAudioFrame(): BufferedFrame.Samples?
    fun extractVideoFrame(): BufferedFrame.Pixels?

    fun startBuffering()

    fun stopBuffering()

    fun clear()

    companion object {
        private const val DEFAULT_CAPACITY = Int.MAX_VALUE

        fun createAudioVideoBuffer(
            decoder: Decoder,
            audioBufferCapacity: Int = DEFAULT_CAPACITY,
            videoBufferCapacity: Int = DEFAULT_CAPACITY,
        ): BufferManager = Implementation(decoder, audioBufferCapacity, videoBufferCapacity)

        fun createAudioOnlyBuffer(
            decoder: Decoder,
            audioBufferCapacity: Int = DEFAULT_CAPACITY,
        ): BufferManager = Implementation(decoder, audioBufferCapacity, 0)
    }

    class Implementation(
        private val decoder: Decoder,
        override val audioBufferCapacity: Int,
        override val videoBufferCapacity: Int = 0,
    ) : BufferManager {

        /**
         * Coroutines
         */

        private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
            if (throwable !is CancellationException) println(throwable.localizedMessage)
        }

        private val bufferContext = Dispatchers.Default + Job()

        private val bufferScope = CoroutineScope(bufferContext)

        private var bufferingJob: Job? = null

        /**
         * Implementation
         */

        private val audioBuffer = LinkedBlockingQueue<BufferedFrame.Samples>(audioBufferCapacity)

        private val videoBuffer = if (decoder.hasVideo()) {
            LinkedBlockingQueue<BufferedFrame.Pixels>(videoBufferCapacity)
        } else null

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

        override fun audioBufferSize() = audioBuffer.size

        override fun videoBufferSize() = videoBuffer?.size

        override fun audioBufferIsEmpty() = audioBuffer.isEmpty()

        override fun videoBufferIsEmpty() = videoBuffer?.isEmpty()

        override fun audioBufferIsNotEmpty() = audioBuffer.isNotEmpty()

        override fun videoBufferIsNotEmpty() = videoBuffer?.isNotEmpty()

        override fun extractAudioFrame() = audioBuffer.poll().also {
            _playbackTimestampMillis.value = it.timestampNanos.nanoseconds.inWholeMilliseconds
            _firstAudioFrame.value = audioBuffer.firstOrNull()
        }

        override fun extractVideoFrame() = videoBuffer?.poll()?.also {
            _playbackTimestampMillis.value = it.timestampNanos.nanoseconds.inWholeMilliseconds
            _firstVideoFrame.value = videoBuffer.firstOrNull()
        }

        override fun startBuffering() {
            bufferingJob = bufferScope.launch(exceptionHandler) {

                isCompleted = false

                while (isActive) {
                    if ((!decoder.hasAudio() || (decoder.hasAudio() && audioBuffer.size < audioBufferCapacity)) && (!decoder.hasVideo() || (decoder.hasVideo() && videoBuffer?.size?.let { it < videoBufferCapacity } == true))) {
                        decoder.nextFrame()?.let { frame ->
                            _bufferTimestampMillis.value = frame.timestampNanos.nanoseconds.inWholeMilliseconds
                            when (frame) {
                                is DecodedFrame.Audio -> {
                                    isCompleted = false

                                    BufferedFrame.Samples(
                                        frame.timestampNanos, frame.bytes
                                    ).let { audioFrame ->
                                        audioBuffer.put(audioFrame).also { _lastAudioFrame.value = audioFrame }
                                    }
                                }

                                is DecodedFrame.Video -> {
                                    isCompleted = false

                                    BufferedFrame.Pixels(
                                        frame.timestampNanos, frame.bytes
                                    ).let { videoFrame ->
                                        videoBuffer?.put(videoFrame)?.also { _lastVideoFrame.value = videoFrame }
                                    }
                                }

                                is DecodedFrame.End -> {
                                    isCompleted = true
                                    return@launch
                                }
                            }
                        }
                    }
                }
            }
        }

        override fun stopBuffering() {
            bufferingJob?.cancel()
            bufferingJob = null
        }

        override fun clear() {
            audioBuffer.clear()
            videoBuffer?.clear()
        }
    }
}
