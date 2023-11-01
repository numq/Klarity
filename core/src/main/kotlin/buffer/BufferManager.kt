package buffer

import decoder.DecodedFrame
import decoder.Decoder
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import media.Media
import java.util.*
import kotlin.time.Duration.Companion.nanoseconds

interface BufferManager {

    val videoBufferCapacity: Int
    val audioBufferCapacity: Int
    val media: Media
    val completionTimestampMillis: Long
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
            audioBufferCapacity: Int?,
            videoBufferCapacity: Int?,
        ): BufferManager = if (!decoder.hasVideo() && !decoder.hasAudio()) {
            throw BufferException.FailedToCreate
        } else {
            Implementation(
                decoder,
                audioBufferCapacity = audioBufferCapacity ?: (
                        decoder.media.frameRate * DEFAULT_CAPACITY_FACTOR
                        ).toInt(),
                videoBufferCapacity = videoBufferCapacity ?: (
                        decoder.media.frameRate * DEFAULT_CAPACITY_FACTOR
                        ).toInt()
            )
        }

        fun createVideoOnlyBuffer(
            decoder: Decoder,
            videoBufferCapacity: Int?,
        ): BufferManager = if (!decoder.hasVideo() && !decoder.hasAudio()) {
            throw BufferException.FailedToCreate
        } else {
            Implementation(
                decoder,
                audioBufferCapacity = ZERO_CAPACITY_FACTOR,
                videoBufferCapacity = videoBufferCapacity ?: (
                        decoder.media.frameRate * DEFAULT_CAPACITY_FACTOR
                        ).toInt()
            )
        }

        fun createAudioOnlyBuffer(
            decoder: Decoder,
            audioBufferCapacity: Int?,
        ): BufferManager = if (!decoder.hasVideo() && !decoder.hasAudio()) {
            throw BufferException.FailedToCreate
        } else {
            Implementation(
                decoder,
                audioBufferCapacity = audioBufferCapacity ?: (
                        decoder.media.frameRate * DEFAULT_CAPACITY_FACTOR
                        ).toInt(),
                videoBufferCapacity = ZERO_CAPACITY_FACTOR
            )
        }
    }

    class Implementation(
        private val decoder: Decoder,
        override val audioBufferCapacity: Int,
        override val videoBufferCapacity: Int,
    ) : BufferManager {

        /**
         * Implementation
         */

        private val audioBuffer = if (decoder.hasAudio()) ArrayDeque<DecodedFrame.Audio>() else null
        private val videoBuffer = if (decoder.hasVideo()) ArrayDeque<DecodedFrame.Video>() else null

        private val _bufferTimestampMillis = MutableStateFlow(0L)
        override val bufferTimestampMillis = _bufferTimestampMillis.asStateFlow()

        private val _playbackTimestampMillis = MutableStateFlow(0L)
        override val playbackTimestampMillis = _playbackTimestampMillis.asStateFlow()

        private val _firstAudioFrame = MutableStateFlow<DecodedFrame.Audio?>(null)
        override val firstAudioFrame = _firstAudioFrame.asStateFlow()

        private val _firstVideoFrame = MutableStateFlow<DecodedFrame.Video?>(null)
        override val firstVideoFrame = _firstVideoFrame.asStateFlow()

        private val _lastAudioFrame = MutableStateFlow<DecodedFrame.Audio?>(null)
        override val lastAudioFrame = _lastAudioFrame.asStateFlow()

        private val _lastVideoFrame = MutableStateFlow<DecodedFrame.Video?>(null)
        override val lastVideoFrame = _lastVideoFrame.asStateFlow()

        override val media = decoder.media

        override var completionTimestampMillis = -1L
            private set

        override fun audioBufferIsNotEmpty() = audioBuffer?.isNotEmpty() == true

        override fun videoBufferIsNotEmpty() = videoBuffer?.isNotEmpty() == true

        override fun extractAudioFrame() = audioBuffer?.poll()?.also {
            _playbackTimestampMillis.value = it.timestampNanos.nanoseconds.inWholeMilliseconds
            _firstAudioFrame.value = audioBuffer.firstOrNull()
        }

        override fun extractVideoFrame() = videoBuffer?.poll()?.also {
            _playbackTimestampMillis.value = it.timestampNanos.nanoseconds.inWholeMilliseconds
            _firstVideoFrame.value = videoBuffer.firstOrNull()
        }

        override suspend fun startBuffering() {

            completionTimestampMillis = -1L

            while (currentCoroutineContext().isActive) {
                decoder.apply {
                    val needMoreFrames = when {
                        hasAudio() && hasVideo() -> {
                            (audioBuffer?.size?.let { it < audioBufferCapacity } == true) || (videoBuffer?.size?.let { it < videoBufferCapacity } == true)
                        }

                        hasAudio() -> {
                            (audioBuffer?.size?.let { it < audioBufferCapacity } == true)
                        }

                        hasVideo() -> {
                            (videoBuffer?.size?.let { it < videoBufferCapacity } == true)
                        }

                        else -> return
                    }

                    if (needMoreFrames) {
                        nextFrame()?.let { frame ->
                            _bufferTimestampMillis.value = frame.timestampNanos.nanoseconds.inWholeMilliseconds
                            when (frame) {
                                is DecodedFrame.Audio -> {
                                    completionTimestampMillis = -1L

                                    audioBuffer?.add(frame).also { _lastAudioFrame.value = frame }
                                }

                                is DecodedFrame.Video -> {
                                    completionTimestampMillis = -1L

                                    videoBuffer?.add(frame).also { _lastVideoFrame.value = frame }
                                }

                                is DecodedFrame.End -> completionTimestampMillis =
                                    frame.timestampNanos.nanoseconds.inWholeMilliseconds
                            }
                        }
                    }
                }
            }

            println("Buffering has been completed")
        }

        override fun flush() {
            audioBuffer?.clear()
            videoBuffer?.clear()

            println("Buffer has been flushed")
        }
    }
}
