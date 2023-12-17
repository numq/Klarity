package decoder

import converter.ByteArrayFrameConverter
import frame.DecodedFrame
import media.Media
import org.bytedeco.ffmpeg.global.avcodec
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FrameGrabber
import java.io.File
import javax.sound.sampled.AudioFormat
import kotlin.time.Duration.Companion.microseconds

interface Decoder : AutoCloseable {

    object Configuration {
        val sampleMode = FrameGrabber.SampleMode.SHORT
        const val audioChannels = 2
        const val audioCodec = avcodec.AV_CODEC_ID_AAC
        const val sampleFormat = avutil.AV_SAMPLE_FMT_S16
        const val sampleRate = 44_100

        val imageMode = FrameGrabber.ImageMode.COLOR
        const val videoCodec = avcodec.AV_CODEC_ID_HEVC
        const val pixelFormat = avutil.AV_PIX_FMT_BGRA

        const val numBuffers = 0
    }

    val isInitialized: Boolean

    val media: Media?

    suspend fun initialize(media: Media)

    suspend fun dispose()

    suspend fun snapshot(timestampMicros: Long): DecodedFrame.Video?

    suspend fun nextFrame(): DecodedFrame?

    suspend fun seekTo(timestampMicros: Long): Long?

    companion object {
        fun createMedia(url: String) = runCatching {
            FFmpegFrameGrabber(url).apply {
                audioChannels = Configuration.audioChannels
                audioCodec = Configuration.audioCodec
                sampleMode = Configuration.sampleMode
                sampleFormat = Configuration.sampleFormat
                sampleRate = Configuration.sampleRate

                videoCodec = Configuration.videoCodec
                imageMode = Configuration.imageMode
                pixelFormat = Configuration.pixelFormat

                numBuffers = Configuration.numBuffers

                startUnsafe()
            }.use { infoGrabber ->
                with(infoGrabber) {
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

                    val previewFrame = runCatching {
                        grabImage()?.use { frame ->
                            ByteArrayFrameConverter().use { converter ->
                                converter.convert(frame)?.let { bytes ->
                                    DecodedFrame.Video(frame.timestamp.microseconds.inWholeNanoseconds, bytes)
                                }
                            }
                        }
                    }.getOrNull()

                    Media(
                        url = url,
                        name = File(url).takeIf(File::exists)?.name,
                        durationNanos = durationNanos,
                        frameRate = frameRate,
                        audioFormat = audioFormat,
                        size = size,
                        previewFrame = previewFrame
                    )
                }
            }
        }.onFailure(Throwable::printStackTrace).getOrNull()

        fun create(): Decoder = DefaultDecoder()
    }
}