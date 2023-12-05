package decoder

import converter.ByteArrayFrameConverter
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import media.Media
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.microseconds

/**
 * Interface representing a decoder for handling media frames.
 */
interface Decoder : AutoCloseable {

    /**
     * Gets the associated [Media] information for the decoder.
     */
    val media: Media

    suspend fun createMedia(url: String): Media?

    /**
     * Retrieves a snapshot of the video frame at the specified timestamp in microseconds.
     *
     * @param timestampMicros The timestamp to capture, in microseconds.
     * @return The snapshot of the video frame at the specified timestamp, or `null` if not available.
     */
    suspend fun snapshot(timestampMicros: Long): DecodedFrame.Video?

    /**
     * Retrieves the next decoded frame from the media.
     *
     * @return The next decoded frame, or `null` if the frame cannot be grabbed for any reason.
     */
    suspend fun nextFrame(): DecodedFrame?

    /**
     * Seeks to the specified timestamp in microseconds.
     *
     * @param timestampMicros The timestamp to seek to, in microseconds.
     * @return The actual timestamp after seeking.
     */
    suspend fun seekTo(timestampMicros: Long): Long

    /**
     * Companion object providing a factory method to create a [Decoder] instance.
     */
    companion object {
        /**
         * Creates a [Decoder] instance for the specified media url.
         *
         * @param mediaUrl The url of the media to decode.
         * @return A [Decoder] instance.
         */
        fun create(mediaUrl: String): Decoder = FFmpegFrameGrabber(mediaUrl).apply {
            audioCodec = avcodec.AV_CODEC_ID_AAC
            sampleMode = FrameGrabber.SampleMode.SHORT
            sampleFormat = avutil.AV_SAMPLE_FMT_S16
            sampleRate = 44100

            videoCodec = avcodec.AV_CODEC_ID_H264
            imageMode = FrameGrabber.ImageMode.COLOR
            pixelFormat = avutil.AV_PIX_FMT_BGRA
        }.let { mediaGrabber ->
            mediaGrabber.start()

            FFmpegFrameGrabber(mediaUrl).apply {
                videoCodec = mediaGrabber.videoCodec
                imageMode = mediaGrabber.imageMode
                pixelFormat = mediaGrabber.pixelFormat
            }.let { snapshotGrabber ->
                snapshotGrabber.start()

                Implementation(
                    mediaGrabber = mediaGrabber,
                    snapshotGrabber = snapshotGrabber
                )
            }
        }
    }
}

private class Implementation(
    private val mediaGrabber: FFmpegFrameGrabber,
    private val snapshotGrabber: FFmpegFrameGrabber,
) : Decoder {

    init {
        avutil.av_log_set_level(avutil.AV_LOG_ERROR)
        FFmpegLogCallback.set()
    }

    private val mediaMutex = Mutex()

    private val snapshotMutex = Mutex()

    private val byteArrayFrameConverter by lazy { ByteArrayFrameConverter() }

    override val media by lazy {
        with(mediaGrabber) {
            val url = formatContext.url().string

            val durationNanos = lengthInTime.microseconds.inWholeNanoseconds

            val audioFormat = if (hasAudio()) AudioFormat(
                sampleRate.toFloat(),
                avutil.av_get_bytes_per_sample(sampleFormat) * 8,
                audioChannels,
                true,
                false
            ) else null

            val size = if (imageWidth > 0 && imageHeight > 0) imageWidth to imageHeight else null

            val frameRate = videoFrameRate.coerceIn(0.0, 60.0)

            Media(
                url = url,
                name = File(url).takeIf(File::exists)?.name,
                durationNanos = durationNanos,
                frameRate = frameRate,
                audioFormat = audioFormat,
                size = size
            )
        }
    }

    override suspend fun createMedia(url: String) = CompletableDeferred<Media?>().apply {
        FFmpegFrameGrabber(url).apply {
            audioCodec = mediaGrabber.audioCodec
            sampleMode = mediaGrabber.sampleMode
            sampleFormat = mediaGrabber.sampleFormat
            sampleRate = mediaGrabber.sampleRate

            videoCodec = mediaGrabber.videoCodec
            imageMode = mediaGrabber.imageMode
            pixelFormat = mediaGrabber.pixelFormat
        }.use { infoGrabber ->
            infoGrabber.start()

            with(infoGrabber) {
                runCatching {
                    val durationNanos = lengthInTime.microseconds.inWholeNanoseconds

                    val audioFormat = if (hasAudio()) AudioFormat(
                        sampleRate.toFloat(),
                        avutil.av_get_bytes_per_sample(sampleFormat) * 8,
                        audioChannels,
                        true,
                        false
                    ) else null

                    val size = if (imageWidth > 0 && imageHeight > 0) imageWidth to imageHeight else null

                    val frameRate = videoFrameRate.coerceIn(0.0, 60.0)

                    Media(
                        url = url,
                        name = File(url).takeIf(File::exists)?.name,
                        durationNanos = durationNanos,
                        frameRate = frameRate,
                        audioFormat = audioFormat,
                        size = size
                    )
                }.getOrNull()?.let(::complete)
            }
        }
    }.await()

    override suspend fun snapshot(timestampMicros: Long) = CompletableDeferred<DecodedFrame.Video?>().apply {
        snapshotMutex.withLock {
            with(snapshotGrabber) {
                setTimestamp(timestampMicros, true)
                grabImage()?.use { frame ->
                    byteArrayFrameConverter.convert(frame)?.let { bytes ->
                        complete(DecodedFrame.Video(frame.timestamp.microseconds.inWholeNanoseconds, bytes))
                    }
                }
            }
        }
    }.await()

    override suspend fun nextFrame(): DecodedFrame? = CompletableDeferred<DecodedFrame?>().apply {
        mediaMutex.withLock {
            with(mediaGrabber) {
                when (val grabbedFrame = grabFrame(
                    hasAudio(), hasVideo(), true, false, false
                )) {
                    null -> complete(DecodedFrame.End(media.durationNanos))
                    else -> grabbedFrame.use { frame ->
                        val timestampNanos = frame.timestamp.microseconds.inWholeNanoseconds
                        val decodedFrame = when (frame.type) {
                            Frame.Type.AUDIO -> byteArrayFrameConverter.convert(frame)
                                ?.let { bytes -> DecodedFrame.Audio(timestampNanos, bytes) }

                            Frame.Type.VIDEO -> byteArrayFrameConverter.convert(frame)
                                ?.let { bytes -> DecodedFrame.Video(timestampNanos, bytes) }

                            else -> null
                        }
                        complete(decodedFrame)
                    }
                }
            }
        }
    }.await()

    override suspend fun seekTo(timestampMicros: Long) = CompletableDeferred<Long>().apply {
        mediaMutex.withLock {
            with(mediaGrabber) {
                setTimestamp(timestampMicros, true)
                complete(timestamp)
            }
        }
    }.await()

    override fun close() {
        mediaGrabber.close()
        snapshotGrabber.close()
        byteArrayFrameConverter.close()
    }
}