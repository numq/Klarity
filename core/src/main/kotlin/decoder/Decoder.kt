package decoder

import extension.pixelBytes
import extension.sampleBytes
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacpp.Loader
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.Frame
import org.bytedeco.javacv.OpenCVFrameConverter
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import player.MediaInfo
import java.io.File
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.microseconds

interface Decoder : AutoCloseable {

    val info: MediaInfo
    fun hasVideo(): Boolean
    fun hasAudio(): Boolean
    fun nextFrame(): DecodedFrame?
    fun seekTo(timestampMicros: Long): Long
    fun stop()

    companion object {
        fun create(url: String, decodeVideo: Boolean, decodeAudio: Boolean): Decoder = runCatching {
            Implementation(
                url,
                decodeVideo = decodeVideo,
                decodeAudio = decodeAudio
            )
        }.getOrElse { throw DecoderException.UnableToCreate }
    }

    class Implementation(
        private val mediaUrl: String,
        private val decodeVideo: Boolean,
        private val decodeAudio: Boolean,
    ) : Decoder {

        init {
            Loader.load(org.bytedeco.opencv.opencv_java::class.java)
        }

        private val grabber = FFmpegFrameGrabber(mediaUrl).apply {
            if (decodeVideo) pixelFormat = avutil.AV_PIX_FMT_BGRA
            sampleFormat = avutil.AV_SAMPLE_FMT_S16
            start()
        }

        private val converter = OpenCVFrameConverter.ToMat()

        private fun resizeVideoFrame(frame: Frame): Frame {
            if (grabber.imageWidth != info.width || grabber.imageHeight != info.height) {
                val output = Mat()
                Imgproc.resize(
                    converter.convertToOrgOpenCvCoreMat(frame),
                    output,
                    Size(info.width.toDouble(), info.height.toDouble())
                )
                return converter.convert(output)
            }
            return frame
        }

        override val info = grabber.run {
            val name = File(mediaUrl).takeIf { it.exists() }?.name ?: mediaUrl

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

            MediaInfo(name, durationNanos, audioFormat, frameRate, width, height)
        }

        override fun hasVideo() = decodeVideo && grabber.run {
            hasVideo() && videoFrameRate > 0 && imageWidth > 0 && imageHeight > 0
        }

        override fun hasAudio() = decodeAudio && grabber.hasAudio()

        override fun nextFrame(): DecodedFrame? = runCatching {
            val frame = grabber.grabFrame(
                decodeAudio,
                decodeVideo,
                true,
                false
            ) ?: return DecodedFrame.End(info.durationNanos)
            val timestamp = frame.timestamp.microseconds.inWholeNanoseconds
            return when (frame.type) {
                Frame.Type.VIDEO -> info.takeIf { it.width > 0 && it.height > 0 }?.run {
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