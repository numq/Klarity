package io.github.numq.klarity.decoder

import io.github.numq.klarity.format.AudioFormat
import io.github.numq.klarity.format.VideoFormat
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.media.Media
import org.jetbrains.skia.Data
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal interface Decoder<Media> {
    val media: Media

    suspend fun decodeAudio(): Result<Frame>

    suspend fun decodeVideo(data: Data): Result<Frame>

    suspend fun seekTo(timestamp: Duration, keyframesOnly: Boolean): Result<Duration>

    suspend fun reset(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        fun probe(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
        ) = runCatching {
            NativeDecoder(
                location = location,
                findAudioStream = findAudioStream,
                findVideoStream = findVideoStream,
                decodeAudioStream = false,
                decodeVideoStream = false
            ).use { decoder ->
                val format = decoder.format.getOrThrow()

                val audioFormat = format.takeIf { fmt ->
                    fmt.sampleRate > 0 && fmt.channels > 0
                }?.let { fmt ->
                    AudioFormat(sampleRate = fmt.sampleRate, channels = fmt.channels)
                }

                val videoFormat = format.takeIf { fmt ->
                    fmt.width > 0 && fmt.height > 0 && fmt.videoBufferCapacity > 0
                }?.let { fmt ->
                    VideoFormat(
                        width = fmt.width,
                        height = fmt.height,
                        frameRate = fmt.frameRate,
                        hardwareAcceleration = HardwareAcceleration.fromNative(fmt.hwDeviceType),
                        bufferCapacity = fmt.videoBufferCapacity
                    )
                }

                when {
                    audioFormat != null && videoFormat != null -> Media.AudioVideo(
                        id = decoder.getNativeHandle(),
                        location = location,
                        duration = format.durationMicros.microseconds,
                        audioFormat = audioFormat,
                        videoFormat = videoFormat
                    )

                    audioFormat != null -> Media.Audio(
                        id = decoder.getNativeHandle(),
                        location = location,
                        duration = format.durationMicros.microseconds,
                        format = audioFormat
                    )

                    videoFormat != null -> Media.Video(
                        id = decoder.getNativeHandle(),
                        location = location,
                        duration = format.durationMicros.microseconds,
                        format = videoFormat
                    )

                    else -> error("Unsupported format")
                }
            }
        }

        fun createAudioDecoder(location: String): Result<Decoder<Media.Audio>> = runCatching {
            val nativeDecoder = NativeDecoder(
                location = location,
                findAudioStream = true,
                findVideoStream = false,
                decodeAudioStream = true,
                decodeVideoStream = false
            )

            try {
                val format = nativeDecoder.format.getOrThrow()

                val audioFormat = format.takeIf { fmt ->
                    fmt.sampleRate > 0 && fmt.channels > 0
                }?.let { fmt ->
                    AudioFormat(
                        sampleRate = fmt.sampleRate, channels = fmt.channels
                    )
                }

                requireNotNull(audioFormat) { "Could not create audio decoder for $location" }

                val media = with(nativeDecoder) {
                    Media.Audio(
                        id = getNativeHandle(),
                        location = location,
                        duration = format.durationMicros.microseconds,
                        format = audioFormat
                    )
                }

                AudioDecoder(nativeDecoder = nativeDecoder, media = media)
            } catch (t: Throwable) {
                nativeDecoder.close()

                throw t
            }
        }

        fun createVideoDecoder(
            location: String,
            hardwareAccelerationCandidates: List<HardwareAcceleration>?,
        ): Result<Decoder<Media.Video>> = runCatching {
            val nativeDecoder = NativeDecoder(
                location = location,
                findAudioStream = false,
                findVideoStream = true,
                decodeAudioStream = false,
                decodeVideoStream = true,
                hardwareAccelerationCandidates = hardwareAccelerationCandidates?.map { candidate ->
                    candidate.native.ordinal
                }?.toIntArray()
            )

            try {
                val format = nativeDecoder.format.getOrThrow()

                val videoFormat = format.takeIf { fmt ->
                    fmt.width > 0 && fmt.height > 0 && fmt.videoBufferCapacity > 0
                }?.let { fmt ->
                    VideoFormat(
                        width = fmt.width,
                        height = fmt.height,
                        frameRate = fmt.frameRate,
                        hardwareAcceleration = HardwareAcceleration.fromNative(fmt.hwDeviceType),
                        bufferCapacity = fmt.videoBufferCapacity
                    )
                }

                requireNotNull(videoFormat) { "Could not create video decoder for $location" }

                val media = with(nativeDecoder) {
                    Media.Video(
                        id = getNativeHandle(),
                        location = location,
                        duration = format.durationMicros.microseconds,
                        format = videoFormat
                    )
                }

                VideoDecoder(nativeDecoder = nativeDecoder, media = media)
            } catch (t: Throwable) {
                nativeDecoder.close()

                throw t
            }
        }
    }
}