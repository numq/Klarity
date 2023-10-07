package buffer

import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import player.MediaInfo
import java.util.*
import kotlin.time.Duration.Companion.milliseconds
import kotlin.time.Duration.Companion.nanoseconds

interface BufferManager {

    val videoBufferCapacity: Int
    val audioBufferCapacity: Int
    val info: MediaInfo
    val isCompleted: Boolean
    val bufferTimestampMillis: StateFlow<Long>
    val playbackTimestampMillis: StateFlow<Long>

    val firstVideoFrame: StateFlow<DecodedFrame.Video?>
    val firstAudioFrame: StateFlow<DecodedFrame.Audio?>

    val lastVideoFrame: StateFlow<DecodedFrame.Video?>
    val lastAudioFrame: StateFlow<DecodedFrame.Audio?>

    fun videoBufferIsNotEmpty(): Boolean
    fun audioBufferIsNotEmpty(): Boolean

    fun extractVideoFrame(): DecodedFrame.Video?
    fun extractAudioFrame(): DecodedFrame.Audio?

    suspend fun startBuffering()
    fun flush()

    companion object {
        private const val ZERO_CAPACITY_FACTOR = 0
        private const val DEFAULT_CAPACITY_FACTOR = 5

        fun createAudioVideoBuffer(
            decoder: Decoder,
            videoBufferCapacity: Int = (decoder.info.frameRate * DEFAULT_CAPACITY_FACTOR).toInt(),
            audioBufferCapacity: Int = (decoder.info.frameRate * DEFAULT_CAPACITY_FACTOR).toInt(),
        ): BufferManager = if (!decoder.hasVideo() && !decoder.hasAudio()) {
            throw BufferException.FailedToCreate
        } else {
            Implementation(
                decoder,
                videoBufferCapacity = videoBufferCapacity,
                audioBufferCapacity = audioBufferCapacity
            )
        }

        fun createVideoOnlyBuffer(
            decoder: Decoder,
            videoBufferCapacity: Int = (decoder.info.frameRate * DEFAULT_CAPACITY_FACTOR).toInt(),
        ): BufferManager = if (!decoder.hasVideo() && !decoder.hasAudio()) {
            throw BufferException.FailedToCreate
        } else {
            Implementation(
                decoder,
                videoBufferCapacity = videoBufferCapacity,
                audioBufferCapacity = ZERO_CAPACITY_FACTOR
            )
        }

        fun createAudioOnlyBuffer(
            decoder: Decoder,
            audioBufferCapacity: Int = (decoder.info.frameRate * DEFAULT_CAPACITY_FACTOR).toInt(),
        ): BufferManager = if (!decoder.hasVideo() && !decoder.hasAudio()) {
            throw BufferException.FailedToCreate
        } else {
            Implementation(
                decoder,
                videoBufferCapacity = ZERO_CAPACITY_FACTOR,
                audioBufferCapacity = audioBufferCapacity
            )
        }
    }

    class Implementation(
        private val decoder: Decoder,
        override val videoBufferCapacity: Int,
        override val audioBufferCapacity: Int,
    ) : BufferManager {

        /**
         * Implementation
         */

        private val videoBuffer =
            if (decoder.hasVideo()) ArrayDeque<DecodedFrame.Video>() else null

        private val audioBuffer =
            if (decoder.hasAudio()) ArrayDeque<DecodedFrame.Audio>() else null

        private val _bufferTimestampMillis = MutableStateFlow(0L)

        override val bufferTimestampMillis: StateFlow<Long>
            get() = _bufferTimestampMillis.asStateFlow()

        private val _playbackTimestampMillis = MutableStateFlow(0L)

        override val playbackTimestampMillis: StateFlow<Long>
            get() = _playbackTimestampMillis.asStateFlow()

        private val _firstVideoFrame = MutableStateFlow<DecodedFrame.Video?>(null)
        override val firstVideoFrame = _firstVideoFrame.asStateFlow()

        private val _firstAudioFrame = MutableStateFlow<DecodedFrame.Audio?>(null)
        override val firstAudioFrame = _firstAudioFrame.asStateFlow()

        private val _lastVideoFrame = MutableStateFlow<DecodedFrame.Video?>(null)
        override val lastVideoFrame = _lastVideoFrame.asStateFlow()

        private val _lastAudioFrame = MutableStateFlow<DecodedFrame.Audio?>(null)
        override val lastAudioFrame = _lastAudioFrame.asStateFlow()

        override val info: MediaInfo
            get() = decoder.info

        override var isCompleted = false
            private set

        override fun videoBufferIsNotEmpty() = videoBuffer?.isNotEmpty() == true

        override fun audioBufferIsNotEmpty() = audioBuffer?.isNotEmpty() == true

        override fun extractVideoFrame() = videoBuffer?.poll()?.also {
            _playbackTimestampMillis.value = it.timestampNanos.nanoseconds.inWholeMilliseconds
            _firstVideoFrame.value = videoBuffer.firstOrNull()
        }

        override fun extractAudioFrame() = audioBuffer?.poll()?.also {
            _playbackTimestampMillis.value = it.timestampNanos.nanoseconds.inWholeMilliseconds
            _firstAudioFrame.value = audioBuffer.firstOrNull()
        }

        override suspend fun startBuffering() {
            CoroutineScope(currentCoroutineContext()).launch {

                decoder.apply {

                    isCompleted = false

                    while (isActive) {

                        val needMoreFrames = when {
                            hasVideo() && hasAudio() -> {
                                (audioBuffer?.size?.let { it < audioBufferCapacity } == true) || (videoBuffer?.size?.let { it < videoBufferCapacity } == true)
                            }

                            hasVideo() -> {
                                (videoBuffer?.size?.let { it < videoBufferCapacity } == true)
                            }

                            hasAudio() -> {
                                (audioBuffer?.size?.let { it < audioBufferCapacity } == true)
                            }

                            else -> return@launch
                        }

                        if (needMoreFrames) {
                            nextFrame()?.let { frame ->
                                _bufferTimestampMillis.value = frame.timestampNanos.nanoseconds.inWholeMilliseconds
                                when (frame) {
                                    is DecodedFrame.Video -> {
                                        isCompleted = false

                                        videoBuffer?.add(frame).also { _lastVideoFrame.value = frame }
                                    }

                                    is DecodedFrame.Audio -> {
                                        isCompleted = false

                                        audioBuffer?.add(frame).also { _lastAudioFrame.value = frame }
                                    }

                                    is DecodedFrame.End -> isCompleted = true
                                }
                            }
                        } else delay((1 / info.frameRate * 1_000L).milliseconds)
                    }
                }
            }.invokeOnCompletion { println("Buffering has been completed") }
        }

        override fun flush() {
            videoBuffer?.clear()
            audioBuffer?.clear()
            println("Buffer has been flushed")
        }
    }
}
