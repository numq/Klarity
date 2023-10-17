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

        private val audioExtensions =
            setOf("mp3", "wav", "aac", "ogg", "wma", "flac", "m4a", "amr", "ac3", "ape", "mid", "ra")

        private val videoExtensions =
            setOf("mp4", "avi", "mkv", "mov", "flv", "wmv", "mpg", "mpeg", "m4v", "webm", "ts", "3gp", "ogv")

        private fun hasCompatibleFormat(fileName: String) = fileName
            .substringAfterLast(".", "")
            .lowercase()
            .let { extension ->
                audioExtensions.contains(extension) || videoExtensions.contains(extension)
            }

        private val grabber = FFmpegFrameGrabber(settings.mediaUrl).apply {
            if (settings.hasVideo) {
                pixelFormat = avutil.AV_PIX_FMT_BGRA
                settings.frameRate?.let(::setFrameRate)
                settings.imageSize?.run {
                    imageWidth = width
                    imageHeight = height
                }
            }
            if (settings.hasAudio) sampleFormat = avutil.AV_SAMPLE_FMT_S16
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

            val audioFormat = if (this@Implementation.hasAudio()) AudioFormat(
                sampleRate.toFloat(),
                avutil.av_get_bytes_per_sample(sampleFormat) * 8,
                audioChannels,
                true,
                false
            ) else null

            val frameRate = when {
                this@Implementation.hasAudio() && this@Implementation.hasVideo() -> frameRate
                this@Implementation.hasVideo() -> videoFrameRate
                this@Implementation.hasAudio() -> audioFrameRate
                else -> 0.0
            }

            val (width, height) = if (this@Implementation.hasVideo()) {
                val size: ImageSize = if (grabber.imageWidth > 1920 || grabber.imageHeight > 1080) {
                    ImageSize((1920 * grabber.aspectRatio).toInt(), (1080 * grabber.aspectRatio).toInt())
                } else ImageSize(grabber.imageWidth, grabber.imageHeight)

                size.width to size.height
            } else 0 to 0

            Media(name, durationNanos, audioFormat, frameRate, width, height)
        }

        override fun hasVideo() = hasCompatibleFormat(settings.mediaUrl) && settings.hasVideo && grabber.hasVideo()

        override fun hasAudio() = hasCompatibleFormat(settings.mediaUrl) && settings.hasAudio && grabber.hasAudio()

        override fun nextFrame(): DecodedFrame? = runCatching {
            val frame = grabber.grabFrame(
                hasAudio(),
                hasVideo(),
                true,
                false
            ) ?: return DecodedFrame.End(media.durationNanos)
            val timestamp = frame.timestamp.microseconds.inWholeNanoseconds
            return when (frame.type) {
                Frame.Type.AUDIO -> frame.sampleBytes()?.let { bytes ->
                    frame.close()
                    return@let DecodedFrame.Audio(timestamp, bytes)
                }

                Frame.Type.VIDEO -> media.takeIf { it.width > 0 && it.height > 0 }?.run {
                    resizeVideoFrame(frame).pixelBytes()?.let { bytes ->
                        frame.close()
                        return@let DecodedFrame.Video(timestamp, bytes)
                    }
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