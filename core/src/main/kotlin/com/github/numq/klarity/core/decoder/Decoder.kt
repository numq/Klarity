package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.format.AudioFormat
import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.media.Media

interface Decoder<Media, Frame> {
    val media: Media

    suspend fun decode(): Result<Frame>

    suspend fun seekTo(timestampMicros: Long, keyframesOnly: Boolean): Result<Long>

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
                findAudioStream = findAudioStream,
                findVideoStream = findVideoStream,
                decodeAudioStream = false,
                decodeVideoStream = false
            ).use { decoder ->
                val audioFormat = decoder.format.takeIf { format ->
                    format.sampleRate > 0 && format.channels > 0
                }?.let { format ->
                    AudioFormat(sampleRate = format.sampleRate, channels = format.channels)
                }

                val videoFormat = decoder.format.takeIf { format ->
                    format.width > 0 && format.height > 0
                }?.let { format ->
                    VideoFormat(
                        width = format.width,
                        height = format.height,
                        frameRate = format.frameRate,
                        hardwareAcceleration = HardwareAcceleration.fromNative(format.hwDeviceType)
                    )
                }

                when {
                    audioFormat != null && videoFormat != null -> Media.AudioVideo(
                        id = decoder.nativeHandle,
                        location = location,
                        durationMicros = decoder.format.durationMicros,
                        audioFormat = audioFormat,
                        videoFormat = videoFormat
                    )

                    audioFormat != null -> Media.Audio(
                        id = decoder.nativeHandle,
                        location = location,
                        durationMicros = decoder.format.durationMicros,
                        format = audioFormat
                    )

                    videoFormat != null -> Media.Video(
                        id = decoder.nativeHandle,
                        location = location,
                        durationMicros = decoder.format.durationMicros,
                        format = videoFormat
                    )

                    else -> error("Unsupported format")
                }
            }
        }

        internal fun createAudioDecoder(
            location: String,
            sampleRate: Int?,
            channels: Int?,
        ): Result<Decoder<Media.Audio, Frame.Audio>> = runCatching {
            val decoder = NativeDecoder(
                location = location,
                findAudioStream = true,
                findVideoStream = false,
                decodeAudioStream = true,
                decodeVideoStream = false,
                sampleRate = sampleRate,
                channels = channels
            )

            val audioFormat = decoder.format.takeIf { format ->
                format.sampleRate > 0 && format.channels > 0
            }?.let { format ->
                AudioFormat(sampleRate = format.sampleRate, channels = format.channels)
            }

            if (audioFormat == null) {
                decoder.close()

                error("Could not create audio decoder for $location")
            }

            val media = with(decoder) {
                Media.Audio(
                    id = nativeHandle,
                    location = location,
                    durationMicros = format.durationMicros,
                    format = audioFormat
                )
            }

            AudioDecoder(decoder = decoder, media = media)
        }

        internal fun createVideoDecoder(
            location: String,
            width: Int?,
            height: Int?,
            frameRate: Double?,
            hardwareAccelerationCandidates: List<HardwareAcceleration>?,
        ): Result<Decoder<Media.Video, Frame.Video>> = runCatching {
            val decoder = NativeDecoder(
                location = location,
                findAudioStream = false,
                findVideoStream = true,
                decodeAudioStream = false,
                decodeVideoStream = true,
                width = width,
                height = height,
                frameRate = frameRate,
                hardwareAccelerationCandidates = hardwareAccelerationCandidates?.map { candidate ->
                    candidate.native.ordinal
                }?.toIntArray()
            )

            val videoFormat = decoder.format.takeIf { format ->
                format.width > 0 && format.height > 0
            }?.let { format ->
                VideoFormat(
                    width = format.width,
                    height = format.height,
                    frameRate = format.frameRate,
                    hardwareAcceleration = HardwareAcceleration.fromNative(format.hwDeviceType)
                )
            }

            if (videoFormat == null) {
                decoder.close()

                error("Could not create video decoder for $location")
            }

            val media = with(decoder) {
                Media.Video(
                    id = nativeHandle,
                    location = location,
                    durationMicros = format.durationMicros,
                    format = videoFormat
                )
            }

            VideoDecoder(decoder = decoder, media = media)
        }
    }
}