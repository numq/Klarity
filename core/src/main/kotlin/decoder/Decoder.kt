package decoder

import converter.ByteArrayFrameConverter
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import media.ImageSize
import media.Media
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.Loader
import org.bytedeco.javacv.*
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import java.io.File
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.microseconds

interface Decoder : AutoCloseable {

    val media: Media
    suspend fun nextFrame(): DecodedFrame?
    suspend fun seekTo(timestampMicros: Long): Long
    suspend fun restart()

    companion object {
        fun create(mediaUrl: String): Decoder? = runCatching {
            FFmpegFrameGrabber(mediaUrl).apply {
                audioCodec = avcodec.AV_CODEC_ID_AAC
                sampleMode = FrameGrabber.SampleMode.SHORT
                sampleFormat = avutil.AV_SAMPLE_FMT_S16
                sampleRate = 44100

                videoCodec = avcodec.AV_CODEC_ID_H264
                imageMode = FrameGrabber.ImageMode.COLOR
                pixelFormat = avutil.AV_PIX_FMT_BGRA

                start()
            }.use { grabber ->
                with(grabber) {
                    val media = with(grabber) {
                        val durationNanos = lengthInTime.microseconds.inWholeNanoseconds

                        val audioFormat = if (hasAudio()) AudioFormat(
                            sampleRate.toFloat(),
                            avutil.av_get_bytes_per_sample(sampleFormat) * 8,
                            audioChannels,
                            true,
                            false
                        ) else null

                        val size = if (hasVideo()) {
                            val size: ImageSize = if (imageWidth > 1920 || imageHeight > 1080) {
                                ImageSize((1920 * aspectRatio).toInt(), (1080 * aspectRatio).toInt())
                            } else ImageSize(imageWidth, imageHeight)

                            size.width to size.height
                        } else null

                        val (audioFrameRate, videoFrameRate) =
                            audioFrameRate.coerceAtLeast(0.0) to videoFrameRate.coerceIn(0.0, 60.0)

                        val media = Media(
                            url = mediaUrl,
                            name = File(mediaUrl).takeIf(File::exists)?.name,
                            durationNanos = durationNanos,
                            audioFrameRate = audioFrameRate,
                            videoFrameRate = videoFrameRate,
                            audioFormat = audioFormat,
                            size = size
                        ).also(::println)

                        media
                    }
                    Implementation(grabber, media)
                }
            }
        }.onFailure { println(it.localizedMessage) }.getOrNull()
    }

    class Implementation(private val mediaGrabber: FFmpegFrameGrabber, override val media: Media) : Decoder {

        init {
            Loader.load(org.bytedeco.opencv.opencv_java::class.java)
            avutil.av_log_set_level(avutil.AV_LOG_ERROR)
            FFmpegLogCallback.set()
        }

        private val grabberMutex = Mutex()

        private val frameMutex = Mutex()

        private val openCvFrameConverter by lazy { OpenCVFrameConverter.ToMat() }

        private val byteArrayFrameConverter by lazy { ByteArrayFrameConverter() }

        private fun resizeVideoFrame(frame: Frame) = runCatching {
            media.size?.let { (width, height) ->
                if (mediaGrabber.imageWidth != width || mediaGrabber.imageHeight != height) {
                    with(openCvFrameConverter) {
                        val output = Mat()
                        Imgproc.resize(
                            convertToOrgOpenCvCoreMat(frame),
                            output,
                            Size(width.toDouble(), height.toDouble())
                        )
                        convert(output)
                    }
                }
            }
            frame
        }.onFailure { println(it.localizedMessage) }.getOrNull()

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

                                Frame.Type.VIDEO -> resizeVideoFrame(frame)
                                    ?.let(byteArrayFrameConverter::convert)
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
            byteArrayFrameConverter.close()
            openCvFrameConverter.close()
        }
    }
}