package fake

import decoder.Decoder
import frame.DecodedFrame
import media.Media
import kotlin.time.Duration.Companion.microseconds

internal class FakeDecoder : Decoder {
    override val isInitialized: Boolean
        get() = true

    override var media: Media? = null
        private set

    override suspend fun initialize(media: Media) {
        this.media = media
    }

    override suspend fun dispose() {
        this.media = null
    }

    override suspend fun snapshot(timestampMicros: Long, size: Pair<Int, Int>?) =
        DecodedFrame.Video(timestampMicros.microseconds.inWholeNanoseconds, false, byteArrayOf())

    override suspend fun nextFrame() = DecodedFrame.Video(System.nanoTime(), false, byteArrayOf())

    override suspend fun seekTo(timestampMicros: Long) = timestampMicros

    override fun close() {

    }
}