package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.format.AudioFormat
import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.media.Location
import com.github.numq.klarity.core.media.Media
import java.io.File
import java.net.URI

interface Decoder<Media, Frame> {
    val media: Media

    suspend fun decode(): Result<Frame>

    suspend fun seekTo(micros: Long, keyframesOnly: Boolean): Result<Unit>

    suspend fun reset(): Result<Unit>

    suspend fun close(): Result<Unit>

    companion object {
        private fun probe(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
        ): Result<Media> = runCatching {
            val mediaLocation = File(location).takeIf(File::exists)?.run {
                Location.Local(path = absolutePath, name = name)
            } ?: URI.create(location).takeIf(URI::isAbsolute)?.run {
                Location.Remote(path = location)
            }

            checkNotNull(mediaLocation) { "Unable to find media" }

            NativeDecoder(
                location = location,
                findAudioStream = findAudioStream,
                findVideoStream = findVideoStream,
                decodeAudioStream = false,
                decodeVideoStream = false,
                sampleRate = 0,
                channels = 0,
                width = 0,
                height = 0,
                frameRate = .0,
                hardwareAccelerationCandidates = intArrayOf()
            ).use { decoder ->
                val format = decoder.format

                val audioFormat = runCatching {
                    check(findAudioStream && format.sampleRate > 0 && format.channels > 0)

                    AudioFormat(sampleRate = format.sampleRate, channels = format.channels)
                }.getOrNull()

                val videoFormat = runCatching {
                    check(findVideoStream && format.width > 0 && format.height > 0)

                    VideoFormat(
                        width = format.width,
                        height = format.height,
                        frameRate = format.frameRate,
                        hardwareAcceleration = HardwareAcceleration.fromNative(format.hwDeviceType)
                    )
                }.getOrNull()

                when {
                    audioFormat != null && videoFormat != null -> Media.AudioVideo(
                        id = decoder.hashCode().toLong(),
                        durationMicros = format.durationMicros,
                        location = mediaLocation,
                        audioFormat = audioFormat,
                        videoFormat = videoFormat
                    )

                    audioFormat != null -> Media.Audio(
                        id = decoder.hashCode().toLong(),
                        durationMicros = format.durationMicros,
                        location = mediaLocation,
                        format = audioFormat,
                    )

                    videoFormat != null -> Media.Video(
                        id = decoder.hashCode().toLong(),
                        durationMicros = format.durationMicros,
                        location = mediaLocation,
                        format = videoFormat,
                    )

                    else -> throw Exception("Unsupported media format")
                }
            }
        }

        internal fun createProbeDecoder(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
        ): Result<Decoder<Media, Frame.Probe>> = probe(
            location = location,
            findAudioStream = findAudioStream,
            findVideoStream = findVideoStream
        ).mapCatching { media ->
            ProbeDecoder(
                decoder = NativeDecoder(
                    location = location,
                    findAudioStream = findAudioStream,
                    findVideoStream = findVideoStream,
                    decodeAudioStream = false,
                    decodeVideoStream = false,
                    sampleRate = 0,
                    channels = 0,
                    width = 0,
                    height = 0,
                    frameRate = .0,
                    hardwareAccelerationCandidates = intArrayOf()
                ), media = media
            )
        }

        internal fun createAudioDecoder(
            location: String,
            sampleRate: Int?,
            channels: Int?,
        ): Result<Decoder<Media.Audio, Frame.Audio>> = probe(
            location = location,
            findAudioStream = true,
            findVideoStream = false
        ).mapCatching<Decoder<Media.Audio, Frame.Audio>, Media> { media ->
            when (media) {
                is Media.AudioVideo -> AudioDecoder(
                    decoder = NativeDecoder(
                        location = location,
                        findAudioStream = true,
                        findVideoStream = false,
                        decodeAudioStream = true,
                        decodeVideoStream = false,
                        sampleRate = sampleRate ?: 0,
                        channels = channels ?: 0,
                        width = 0,
                        height = 0,
                        frameRate = .0,
                        hardwareAccelerationCandidates = intArrayOf()
                    ), media = media.toAudio()
                )

                is Media.Audio -> AudioDecoder(
                    decoder = NativeDecoder(
                        location = location,
                        findAudioStream = true,
                        findVideoStream = false,
                        decodeAudioStream = true,
                        decodeVideoStream = false,
                        sampleRate = sampleRate ?: 0,
                        channels = channels ?: 0,
                        width = 0,
                        height = 0,
                        frameRate = .0,
                        hardwareAccelerationCandidates = intArrayOf()
                    ), media = media
                )

                is Media.Video -> throw Exception("Unable to create audio decoder")
            }
        }

        internal fun createVideoDecoder(
            location: String,
            width: Int?,
            height: Int?,
            frameRate: Double?,
            hardwareAccelerationCandidates: List<HardwareAcceleration>,
        ): Result<Decoder<Media.Video, Frame.Video>> = probe(
            location = location,
            findAudioStream = false,
            findVideoStream = true
        ).mapCatching { media ->
            when (media) {
                is Media.AudioVideo -> VideoDecoder(
                    decoder = NativeDecoder(
                        location = location,
                        findAudioStream = false,
                        findVideoStream = true,
                        decodeAudioStream = false,
                        decodeVideoStream = true,
                        sampleRate = 0,
                        channels = 0,
                        width = width ?: 0,
                        height = height ?: 0,
                        frameRate = frameRate ?: .0,
                        hardwareAccelerationCandidates = hardwareAccelerationCandidates.map { candidate ->
                            candidate.native.ordinal
                        }.toIntArray()
                    ), media = media.toVideo()
                )

                is Media.Audio -> throw Exception("Unable to create video decoder")

                is Media.Video -> VideoDecoder(
                    decoder = NativeDecoder(
                        location = location,
                        findAudioStream = false,
                        findVideoStream = true,
                        decodeAudioStream = false,
                        decodeVideoStream = true,
                        sampleRate = 0,
                        channels = 0,
                        width = width ?: 0,
                        height = height ?: 0,
                        frameRate = frameRate ?: .0,
                        hardwareAccelerationCandidates = hardwareAccelerationCandidates.map { candidate ->
                            candidate.native.ordinal
                        }.toIntArray()
                    ), media = media
                )
            }
        }
    }
}