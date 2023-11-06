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
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.microseconds

interface Decoder : AutoCloseable {

    val media: Media
    fun timestampMicros(): Long
    suspend fun snapshot(): DecodedFrame.Video?
    suspend fun nextFrame(): DecodedFrame?
    suspend fun seekTo(timestampMicros: Long): Long
    suspend fun restart()

    companion object {
        fun create(mediaUrl: String): Decoder? = runCatching {
            Implementation(mediaUrl)
        }.onFailure { println(it.localizedMessage) }.getOrNull()
    }

    class Implementation(private val mediaUrl: String) : Decoder {

        init {
            Loader.load(org.bytedeco.opencv.opencv_java::class.java)
            avutil.av_log_set_level(Thread.MAX_PRIORITY)
            FFmpegLogCallback.set()
        }

        private val mutex = Mutex()

        private val openCvFrameConverter by lazy { OpenCVFrameConverter.ToMat() }

        private val byteArrayFrameConverter by lazy { ByteArrayFrameConverter() }

        private val mediaGrabber = FFmpegFrameGrabber(mediaUrl).apply {
            audioCodec = avcodec.AV_CODEC_ID_AAC
            sampleMode = FrameGrabber.SampleMode.SHORT
            sampleFormat = avutil.AV_SAMPLE_FMT_S16

            videoCodec = avcodec.AV_CODEC_ID_H264
            imageMode = FrameGrabber.ImageMode.COLOR
            pixelFormat = avutil.AV_PIX_FMT_BGRA

            setOption("preset", "veryfast")
        }.also(FFmpegFrameGrabber::start)

        private val snapshotGrabber = FFmpegFrameGrabber(mediaUrl).apply {
            audioCodec = avcodec.AV_CODEC_ID_AAC
            sampleMode = FrameGrabber.SampleMode.SHORT
            sampleFormat = avutil.AV_SAMPLE_FMT_S16

            videoCodec = avcodec.AV_CODEC_ID_H264
            imageMode = FrameGrabber.ImageMode.COLOR
            pixelFormat = avutil.AV_PIX_FMT_BGRA

            setOption("preset", "veryfast")
        }.also(FFmpegFrameGrabber::start)

        private fun resizeVideoFrame(frame: Frame) = runCatching {
            media.size?.let { (width, height) ->
                if (mediaGrabber.imageWidth != width || mediaGrabber.imageHeight != height) {
                    val output = Mat()
                    Imgproc.resize(
                        openCvFrameConverter.convertToOrgOpenCvCoreMat(frame),
                        output,
                        Size(width.toDouble(), height.toDouble())
                    )
                    return@runCatching openCvFrameConverter.convert(output)
                }
            }
            frame
        }.onFailure { println(it.localizedMessage) }.getOrNull()

        override val media = mediaGrabber.run {
            val durationNanos = lengthInTime.microseconds.inWholeNanoseconds

            val audioFormat = if (hasAudio()) AudioFormat(
                sampleRate.toFloat(), avutil.av_get_bytes_per_sample(sampleFormat) * 8, audioChannels, true, false
            ) else null

            val size = if (hasVideo()) {
                val size: ImageSize = if (imageWidth > 1920 || imageHeight > 1080) {
                    ImageSize((1920 * aspectRatio).toInt(), (1080 * aspectRatio).toInt())
                } else ImageSize(imageWidth, imageHeight)

                size.width to size.height
            } else null

            Media(
                name = mediaUrl,
                durationNanos = durationNanos,
                audioFrameRate = audioFrameRate,
                videoFrameRate = videoFrameRate,
                audioFormat = audioFormat,
                size = size
            )
        }

        override fun timestampMicros() = mediaGrabber.timestamp

        override suspend fun snapshot(): DecodedFrame.Video? = snapshotGrabber.runCatching {
            mutex.withLock {
                val initialTimestampMicros = timestamp
                val frame = grabImage()?.use { frame ->
                    ByteArrayFrameConverter().convert(frame)?.let { bytes ->
                        DecodedFrame.Video(frame.timestamp.microseconds.inWholeNanoseconds, bytes)
                    }
                }
                timestamp = initialTimestampMicros
                frame
            }
        }.onFailure { println(it.localizedMessage) }.getOrNull()

        override suspend fun nextFrame(): DecodedFrame? = mediaGrabber.runCatching {
            mutex.withLock {
                when (val grabbedFrame = grabFrame(
                    hasAudio(), hasVideo(), true, false
                )) {
                    null -> return DecodedFrame.End(media.durationNanos)
                    else -> grabbedFrame.use { frame ->
                        val timestampNanos = frame.timestamp.microseconds.inWholeNanoseconds
                        when (frame.type) {
                            Frame.Type.AUDIO -> byteArrayFrameConverter.convert(frame)?.let { bytes ->
                                DecodedFrame.Audio(timestampNanos, bytes)
                            }

                            Frame.Type.VIDEO -> resizeVideoFrame(frame)
                                ?.let(byteArrayFrameConverter::convert)
                                ?.let { bytes ->
                                    DecodedFrame.Video(timestampNanos, bytes)
                                }

                            else -> null
                        }
                    }
                }
            }
        }.onFailure { println(it.localizedMessage) }.getOrNull()

        override suspend fun seekTo(timestampMicros: Long) = mutex.withLock {
            mediaGrabber.setTimestamp(timestampMicros, true)
            mediaGrabber.timestamp
        }

        override suspend fun restart() = mutex.withLock {
            mediaGrabber.restart()
        }

        override fun close() {
            snapshotGrabber.close()
            mediaGrabber.close()
        }
    }
}