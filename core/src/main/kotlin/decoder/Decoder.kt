package decoder

import extension.pixelBytes
import extension.sampleBytes
import media.ImageSize
import media.Media
import media.MediaSettings
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.Loader
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.OpenCVFrameConverter
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.microseconds

interface Decoder : AutoCloseable {

    val media: Media
    fun hasVideo(): Boolean
    fun hasAudio(): Boolean
    fun nextFrame(): DecodedFrame?
    fun seekTo(timestampMicros: Long): Long
    fun stop()

    companion object {
        fun create(settings: MediaSettings): Decoder = runCatching {
            Implementation(settings)
        }.getOrElse { throw DecoderException.UnableToCreate }
    }

    class Implementation(private val settings: MediaSettings) : Decoder {

        init {
            Loader.load(org.bytedeco.opencv.opencv_java::class.java)
        }

        private val grabber = FFmpegFrameGrabber(settings.mediaUrl).apply {
            if (settings.hasVideo) pixelFormat = avutil.AV_PIX_FMT_BGRA
            if (settings.hasAudio) sampleFormat = avutil.AV_SAMPLE_FMT_S16
            settings.imageSize?.run {
                imageWidth = width
                imageHeight = height
            }
            settings.frameRate?.let(::setFrameRate)
            start()
        }

        private val converter = OpenCVFrameConverter.ToMat()

        private fun resizeVideoFrame(frame: Frame): Frame {
            if (grabber.imageWidth != media.width || grabber.imageHeight != media.height) {
                val output = Mat()
                Imgproc.resize(
                    converter.convertToOrgOpenCvCoreMat(frame),
                    output,
                    Size(media.width.toDouble(), media.height.toDouble())
                )
                return converter.convert(output)
            }
            return frame
        }

        override val media = grabber.run {
            val name = settings.mediaUrl

            val durationNanos = lengthInTime.microseconds.inWholeNanoseconds

            val audioFormat = AudioFormat(
                sampleRate.toFloat(),
                avutil.av_get_bytes_per_sample(sampleFormat) * 8,
                audioChannels,
                true,
                false
            )

            val frameRate = when {
                hasAudio() && hasVideo() -> frameRate
                hasVideo() -> videoFrameRate
                hasAudio() -> audioFrameRate
                else -> 0.0
            }

            val (width, height) = if (hasVideo()) {
                val size: ImageSize = if (grabber.imageWidth > 1920 || grabber.imageHeight > 1080) {
                    ImageSize((1920 * grabber.aspectRatio).toInt(), (1080 * grabber.aspectRatio).toInt())
                } else ImageSize(grabber.imageWidth, grabber.imageHeight)

                size.width to size.height
            } else 0 to 0

            Media(name, durationNanos, audioFormat, frameRate, width, height)
        }

        override fun hasVideo() = settings.hasVideo && grabber.run {
            hasVideo() && videoFrameRate > 0 && imageWidth > 0 && imageHeight > 0
        }

        override fun hasAudio() = settings.hasAudio && grabber.hasAudio()

        override fun nextFrame(): DecodedFrame? = runCatching {
            val frame = grabber.grabFrame(
                settings.hasAudio,
                settings.hasVideo,
                true,
                false
            ) ?: return DecodedFrame.End(media.durationNanos)
            val timestamp = frame.timestamp.microseconds.inWholeNanoseconds
            return when (frame.type) {
                Frame.Type.VIDEO -> media.takeIf { it.width > 0 && it.height > 0 }?.run {
                    resizeVideoFrame(frame).pixelBytes()?.let { bytes ->
                        frame.close()
                        return@let DecodedFrame.Video(timestamp, bytes)
                    }
                }

                Frame.Type.AUDIO -> frame.sampleBytes()?.let { bytes ->
                    frame.close()
                    return@let DecodedFrame.Audio(timestamp, bytes)
                }

                else -> null
            }
        }.onFailure { println(it.localizedMessage) }.getOrNull()

        override fun seekTo(timestampMicros: Long): Long {
            grabber.setTimestamp(timestampMicros, true)
            return grabber.timestamp
        }

        override fun stop() {
            grabber.restart()
        }

        override fun close() {
            grabber.stop()
            grabber.close()
            grabber.release()
        }
    }
}