package decoder

import converter.ByteArrayFrameConverter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import media.Media
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.Loader
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

    /**
     * Retrieves a snapshot of the video frame at the specified timestamp in microseconds.
     * @param timestampMicros The timestamp to capture, in microseconds.
     * @return The snapshot of the video frame at the specified timestamp, or `null` if not available.
     */
    suspend fun snapshot(timestampMicros: Long): DecodedFrame.Video?

    /**
     * Retrieves the next decoded frame from the media.
     * @return The next decoded frame, or `null` if the frame cannot be grabbed for any reason.
     */
    suspend fun nextFrame(): DecodedFrame?

    /**
     * Seeks to the specified timestamp in microseconds.
     * @param timestampMicros The timestamp to seek to, in microseconds.
     * @return The actual timestamp after seeking.
     */
    suspend fun seekTo(timestampMicros: Long): Long

    /**
     * Restarts the media decoding process.
     */
    suspend fun restart()

    /**
     * Companion object providing a factory method to create a [Decoder] instance.
     */
    companion object {
        /**
         * Creates a [Decoder] instance for the specified media url.
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

            start()
        }.use { mediaGrabber ->
            with(mediaGrabber) {
                val media = with(mediaGrabber) {
                    val durationNanos = lengthInTime.microseconds.inWholeNanoseconds

                    val audioFormat = if (hasAudio()) AudioFormat(
                        sampleRate.toFloat(),
                        avutil.av_get_bytes_per_sample(sampleFormat) * 8,
                        audioChannels,
                        true,
                        false
                    ) else null

                    val size = imageWidth to imageHeight

                    val (audioFrameRate, videoFrameRate) = audioFrameRate.coerceIn(
                        0.0,
                        192.0
                    ) to videoFrameRate.coerceIn(0.0, 60.0)

                    val previewFrame = restart().let {
                        mediaGrabber.grabImage()?.run {
                            ByteArrayFrameConverter().use { converter ->
                                converter.convert(this)?.let { bytes ->
                                    DecodedFrame.Video(timestamp.microseconds.inWholeNanoseconds, bytes)
                                }
                            }
                        }
                    }

                    val media = Media(
                        url = mediaUrl,
                        name = File(mediaUrl).takeIf(File::exists)?.name,
                        durationNanos = durationNanos,
                        audioFrameRate = audioFrameRate,
                        videoFrameRate = videoFrameRate,
                        audioFormat = audioFormat,
                        size = size,
                        previewFrame = previewFrame
                    )

                    media
                }

                FFmpegFrameGrabber(media.url).apply {
                    videoCodec = mediaGrabber.videoCodec
                    imageMode = mediaGrabber.imageMode
                    pixelFormat = mediaGrabber.pixelFormat
                }.let { snapshotGrabber ->
                    Implementation(mediaGrabber = mediaGrabber, snapshotGrabber = snapshotGrabber, media)
                }
            }
        }
    }

    private class Implementation(
        private val mediaGrabber: FFmpegFrameGrabber,
        private val snapshotGrabber: FFmpegFrameGrabber,
        override val media: Media,
    ) : Decoder {

        init {
            Loader.load(org.bytedeco.opencv.opencv_java::class.java)
            avutil.av_log_set_level(avutil.AV_LOG_ERROR)
            FFmpegLogCallback.set()
            snapshotGrabber.start()
        }

        private val grabberMutex = Mutex()

        private val frameMutex = Mutex()

        private val byteArrayFrameConverter by lazy { ByteArrayFrameConverter() }

        override suspend fun snapshot(timestampMicros: Long) = snapshotGrabber.run {
            setTimestamp(timestampMicros, true)
            grabImage()?.let { frame ->
                byteArrayFrameConverter.convert(frame)?.let { bytes ->
                    DecodedFrame.Video(frame.timestamp.microseconds.inWholeNanoseconds, bytes)
                }
            }
        }

        override suspend fun nextFrame(): DecodedFrame? = grabberMutex.withLock {
            mediaGrabber.runCatching {
                when (val grabbedFrame = grabFrame(
                    hasAudio(), hasVideo(), true, false, false
                )) {
                    null -> return DecodedFrame.End(media.durationNanos)
                    else -> frameMutex.withLock {
                        grabbedFrame.use { frame ->
                            val timestampNanos = frame.timestamp.microseconds.inWholeNanoseconds
                            when (frame.type) {
                                Frame.Type.AUDIO -> byteArrayFrameConverter.convert(frame)
                                    ?.let { bytes -> DecodedFrame.Audio(timestampNanos, bytes) }

                                Frame.Type.VIDEO -> byteArrayFrameConverter.convert(frame)
                                    ?.let { bytes -> DecodedFrame.Video(timestampNanos, bytes) }

                                else -> null
                            }
                        }
                    }
                }
            }
        }.onFailure { println(it.localizedMessage) }.getOrNull()

        override suspend fun seekTo(timestampMicros: Long) = grabberMutex.withLock {
            with(mediaGrabber) {
                setTimestamp(timestampMicros, true)
                timestamp
            }
        }

        override suspend fun restart() = grabberMutex.withLock {
            mediaGrabber.restart()
        }

        override fun close() {
            snapshotGrabber.close()
            byteArrayFrameConverter.close()
        }
    }
}