package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.format.AudioFormat
import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.media.Media
import kotlin.time.Duration
import kotlin.time.Duration.Companion.microseconds

internal interface Decoder<Media> {
    val media: Media

    suspend fun decode(): Result<Frame>

    suspend fun seekTo(timestamp: Duration, keyframesOnly: Boolean): Result<Duration>

    suspend fun reset(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        internal fun probe(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
        ) = runCatching {
            NativeDecoder(
                location = location,
                audioFramePoolCapacity = if (findAudioStream) 0 else -1,
                videoFramePoolCapacity = if (findVideoStream) 0 else -1
            ).use { decoder ->
                val format = decoder.format.getOrThrow()

                check(format.durationMicros > 0L) { "Media does not support playback" }

                val audioFormat = format.takeIf { fmt ->
                    fmt.sampleRate > 0 && fmt.channels > 0
                }?.let { fmt ->
                    AudioFormat(sampleRate = fmt.sampleRate, channels = fmt.channels)
                }

                val videoFormat = format.takeIf { fmt ->
                    fmt.width > 0 && fmt.height > 0
                }?.let { fmt ->
                    VideoFormat(
                        width = fmt.width,
                        height = fmt.height,
                        frameRate = fmt.frameRate,
                        hardwareAcceleration = HardwareAcceleration.fromNative(fmt.hwDeviceType)
                    )
                }

                when {
                    audioFormat != null && videoFormat != null -> Media.AudioVideo(
                        id = decoder.nativeHandle.get(),
                        location = location,
                        duration = format.durationMicros.microseconds,
                        audioFormat = audioFormat,
                        videoFormat = videoFormat
                    )

                    audioFormat != null -> Media.Audio(
                        id = decoder.nativeHandle.get(),
                        location = location,
                        duration = format.durationMicros.microseconds,
                        format = audioFormat
                    )

                    videoFormat != null -> Media.Video(
                        id = decoder.nativeHandle.get(),
                        location = location,
                        duration = format.durationMicros.microseconds,
                        format = videoFormat
                    )

                    else -> error("Unsupported format")
                }
            }
        }

        internal fun createAudioDecoder(
            location: String,
            framePoolCapacity: Int,
            sampleRate: Int?,
            channels: Int?,
        ): Result<Decoder<Media.Audio>> = runCatching {
            require(framePoolCapacity >= 0) { "Invalid audio frame pool capacity" }

            val nativeDecoder = NativeDecoder(
                location = location,
                audioFramePoolCapacity = framePoolCapacity,
                videoFramePoolCapacity = -1,
                sampleRate = sampleRate,
                channels = channels
            )

            try {
                val format = nativeDecoder.format.getOrThrow()

                val audioFormat = format.takeIf { fmt ->
                    fmt.sampleRate > 0 && fmt.channels > 0
                }?.let { fmt ->
                    AudioFormat(sampleRate = fmt.sampleRate, channels = fmt.channels)
                }

                requireNotNull(audioFormat) { "Could not create audio decoder for $location" }

                val media = with(nativeDecoder) {
                    Media.Audio(
                        id = nativeHandle.get(),
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

        internal fun createVideoDecoder(
            location: String,
            framePoolCapacity: Int,
            width: Int?,
            height: Int?,
            hardwareAccelerationCandidates: List<HardwareAcceleration>?,
        ): Result<Decoder<Media.Video>> = runCatching {
            require(framePoolCapacity >= 0) { "Invalid video frame pool capacity" }

            val nativeDecoder = NativeDecoder(
                location = location,
                audioFramePoolCapacity = -1,
                videoFramePoolCapacity = framePoolCapacity,
                width = width,
                height = height,
                hardwareAccelerationCandidates = hardwareAccelerationCandidates?.map { candidate ->
                    candidate.native.ordinal
                }?.toIntArray()
            )

            try {
                val format = nativeDecoder.format.getOrThrow()

                val videoFormat = format.takeIf { fmt ->
                    fmt.width > 0 && fmt.height > 0
                }?.let { fmt ->
                    VideoFormat(
                        width = fmt.width,
                        height = fmt.height,
                        frameRate = fmt.frameRate,
                        hardwareAcceleration = HardwareAcceleration.fromNative(fmt.hwDeviceType)
                    )
                }

                requireNotNull(videoFormat) { "Could not create video decoder for $location" }

                val media = with(nativeDecoder) {
                    Media.Video(
                        id = nativeHandle.get(),
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