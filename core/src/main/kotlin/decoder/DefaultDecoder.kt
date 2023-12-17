package decoder

import converter.ByteArrayFrameConverter
import extension.suspend
import frame.DecodedFrame
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import media.Media
import org.bytedeco.ffmpeg.global.avutil
import org.bytedeco.javacv.FFmpegFrameGrabber
import org.bytedeco.javacv.FFmpegLogCallback
import org.bytedeco.javacv.Frame
import kotlin.time.Duration.Companion.microseconds

internal class DefaultDecoder : Decoder {

    init {
        FFmpegLogCallback.set()
        avutil.av_log_set_level(avutil.AV_LOG_ERROR)
    }

    private val mediaFrameConverter by lazy { ByteArrayFrameConverter() }

    private val snapshotFrameConverter by lazy { ByteArrayFrameConverter() }

    private val initializationMutex = Mutex()

    private val mediaMutex = Mutex()

    private val snapshotMutex = Mutex()

    private var mediaGrabber: FFmpegFrameGrabber? = null

    private var snapshotGrabber: FFmpegFrameGrabber? = null

    override var isInitialized = false
        private set

    override var media: Media? = null
        private set

    override suspend fun initialize(media: Media) = initializationMutex.withLock {

        if (isInitialized) throw DecoderException.AlreadyInitialized

        runCatching {
            this.media = media.apply {
                mediaMutex.withLock {
                    mediaGrabber = FFmpegFrameGrabber(url).apply {
                        audioChannels = Decoder.Configuration.audioChannels
                        audioCodec = Decoder.Configuration.audioCodec
                        sampleMode = Decoder.Configuration.sampleMode
                        sampleFormat = Decoder.Configuration.sampleFormat
                        sampleRate = Decoder.Configuration.sampleRate

                        videoCodec = Decoder.Configuration.videoCodec
                        imageMode = Decoder.Configuration.imageMode
                        pixelFormat = Decoder.Configuration.pixelFormat

                        numBuffers = Decoder.Configuration.numBuffers

                        startUnsafe()
                    }
                }

                snapshotMutex.withLock {
                    snapshotGrabber = FFmpegFrameGrabber(url).apply {
                        videoCodec = Decoder.Configuration.videoCodec
                        imageMode = Decoder.Configuration.imageMode
                        pixelFormat = Decoder.Configuration.pixelFormat

                        numBuffers = Decoder.Configuration.numBuffers

                        startUnsafe()
                    }
                }
            }

            isInitialized = true
        }
    }.suspend()

    override suspend fun dispose() = initializationMutex.withLock {

        if (!isInitialized) throw DecoderException.UnableToInitialize

        runCatching {
            mediaMutex.withLock {
                mediaGrabber?.close()
                mediaGrabber = null
            }

            snapshotMutex.withLock {
                snapshotGrabber?.close()
                snapshotGrabber = null
            }

            media = null

            isInitialized = false
        }
    }.suspend()

    override suspend fun snapshot(timestampMicros: Long) = snapshotMutex.withLock {
        snapshotGrabber?.runCatching {
            flush()
            setTimestamp(timestampMicros, true)
            grabImage()?.use { frame ->
                snapshotFrameConverter.convert(frame)?.let { bytes ->
                    DecodedFrame.Video(frame.timestamp.microseconds.inWholeNanoseconds, bytes)
                }
            }
        }
    }?.suspend()

    override suspend fun nextFrame(): DecodedFrame? = mediaMutex.withLock {
        mediaGrabber?.runCatching {
            media?.run {
                when (val grabbedFrame = grabFrame(
                    hasAudio(), hasVideo(), true, false, false
                )) {
                    null -> DecodedFrame.End(durationNanos)
                    else -> grabbedFrame.use { frame ->
                        val timestampNanos = frame.timestamp.microseconds.inWholeNanoseconds
                        when (frame.type) {
                            Frame.Type.AUDIO -> mediaFrameConverter.convert(frame)
                                ?.let { bytes -> DecodedFrame.Audio(timestampNanos, bytes) }

                            Frame.Type.VIDEO -> mediaFrameConverter.convert(frame)
                                ?.let { bytes -> DecodedFrame.Video(timestampNanos, bytes) }

                            else -> null
                        }
                    }
                }
            }
        }
    }?.suspend()

    override suspend fun seekTo(timestampMicros: Long) = mediaMutex.withLock {
        mediaGrabber?.runCatching {
            flush()
            setTimestamp(timestampMicros, true)
            timestamp
        }
    }?.suspend()

    override fun close() {
        mediaGrabber?.close()
        mediaGrabber = null

        snapshotGrabber?.close()
        snapshotGrabber = null

        mediaFrameConverter.close()
        snapshotFrameConverter.close()
    }
}