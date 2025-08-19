package io.github.numq.klarity.decoder

import io.github.numq.klarity.format.Format
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.media.Media
import org.jetbrains.skia.Data
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal interface Decoder<Format> {
    val location: String

    val duration: Duration

    val format: Format

    suspend fun decodeAudio(): Result<Frame>

    suspend fun decodeVideo(data: Data): Result<Frame>

    suspend fun seekTo(timestamp: Duration, keyFramesOnly: Boolean): Result<Unit>

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
                val nativeFormat = decoder.format.getOrThrow()

                val duration = nativeFormat.durationMicros.microseconds

                val audioFormat = nativeFormat.takeIf { fmt ->
                    fmt.sampleRate > 0 && fmt.channels > 0
                }?.let { fmt ->
                    Format.Audio(sampleRate = fmt.sampleRate, channels = fmt.channels)
                }

                val videoFormat = nativeFormat.takeIf { fmt ->
                    fmt.width > 0 && fmt.height > 0 && fmt.videoBufferCapacity > 0
                }?.let { fmt ->
                    Format.Video(
                        width = fmt.width,
                        height = fmt.height,
                        frameRate = fmt.frameRate,
                        hardwareAcceleration = HardwareAcceleration.fromNative(fmt.hwDeviceType),
                        bufferCapacity = fmt.videoBufferCapacity
                    )
                }

                check(audioFormat != null || videoFormat != null) { "Unsupported format" }

                Media(
                    id = decoder.getNativeHandle(),
                    location = location,
                    duration = duration,
                    audioFormat = audioFormat,
                    videoFormat = videoFormat
                )
            }
        }

        fun createAudioDecoder(location: String): Result<Decoder<Format.Audio>> = runCatching {
            val nativeDecoder = NativeDecoder(
                location = location,
                findAudioStream = true,
                findVideoStream = false,
                decodeAudioStream = true,
                decodeVideoStream = false
            )

            try {
                val nativeFormat = nativeDecoder.format.getOrThrow()

                val duration = nativeFormat.durationMicros.microseconds

                val format = nativeFormat.takeIf { fmt ->
                    fmt.sampleRate > 0 && fmt.channels > 0
                }?.let { fmt ->
                    Format.Audio(
                        sampleRate = fmt.sampleRate, channels = fmt.channels
                    )
                }

                requireNotNull(format) { "Could not create audio decoder for $location" }

                AudioDecoder(
                    nativeDecoder = nativeDecoder, location = location, duration = duration, format = format
                )
            } catch (t: Throwable) {
                nativeDecoder.close()

                throw t
            }
        }

        fun createVideoDecoder(
            location: String,
            hardwareAccelerationCandidates: List<HardwareAcceleration>?,
        ): Result<Decoder<Format.Video>> = runCatching {
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
                val nativeFormat = nativeDecoder.format.getOrThrow()

                val duration = nativeFormat.durationMicros.microseconds

                val videoFormat = nativeFormat.takeIf { fmt ->
                    fmt.width > 0 && fmt.height > 0 && fmt.videoBufferCapacity > 0
                }?.let { fmt ->
                    Format.Video(
                        width = fmt.width,
                        height = fmt.height,
                        frameRate = fmt.frameRate,
                        hardwareAcceleration = HardwareAcceleration.fromNative(fmt.hwDeviceType),
                        bufferCapacity = fmt.videoBufferCapacity
                    )
                }

                requireNotNull(videoFormat) { "Could not create video decoder for $location" }

                VideoDecoder(
                    nativeDecoder = nativeDecoder, location = location, duration = duration, format = videoFormat
                )
            } catch (t: Throwable) {
                nativeDecoder.close()

                throw t
            }
        }
    }
}