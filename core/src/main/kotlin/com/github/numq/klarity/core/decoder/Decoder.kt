package com.github.numq.klarity.core.decoder

import com.github.numq.klarity.core.format.AudioFormat
import com.github.numq.klarity.core.format.VideoFormat
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.media.Location
import com.github.numq.klarity.core.media.Media
import java.io.File
import java.net.URI

interface Decoder<Media, Frame> : AutoCloseable {
    val media: Media
    suspend fun nextFrame(width: Int?, height: Int?): Result<Frame>
    suspend fun seekTo(micros: Long, keyframesOnly: Boolean): Result<Unit>
    suspend fun reset(): Result<Unit>

    companion object {
        private fun probe(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
            hardwareAcceleration: HardwareAcceleration,
        ): Result<Media> = runCatching {
            val mediaLocation = File(location).takeIf(File::exists)?.run {
                Location.Local(path = absolutePath, name = name)
            } ?: URI.create(location).takeIf(URI::isAbsolute)?.run {
                Location.Remote(path = location)
            }

            checkNotNull(mediaLocation) { "Unable to find media" }

            val media: Media

            NativeDecoder(location, findAudioStream, findVideoStream, hardwareAcceleration).use { decoder ->
                val format = decoder.format

                val audioFormat = runCatching {
                    if (findAudioStream) {
                        check(format.sampleRate > 0 && format.channels > 0) { "Audio decoding is not supported by media" }

                        AudioFormat(
                            sampleRate = format.sampleRate,
                            channels = format.channels
                        )
                    } else null
                }.getOrNull()

                val videoFormat = runCatching {
                    if (findVideoStream) {
                        check(format.width > 0 && format.height > 0 && format.frameRate >= 0) { "Video decoding is not supported by media" }

                        VideoFormat(
                            width = format.width,
                            height = format.height,
                            frameRate = format.frameRate
                        )
                    } else null
                }.getOrNull()

                media = when {
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

            media
        }

        internal fun createProbeDecoder(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
            hardwareAcceleration: HardwareAcceleration,
        ): Result<Decoder<Media, Frame.Probe>> = probe(
            location = location,
            findAudioStream = findAudioStream,
            findVideoStream = findVideoStream,
            hardwareAcceleration = hardwareAcceleration
        ).mapCatching { media ->
            ProbeDecoder(
                decoder = NativeDecoder(
                    location = location,
                    findAudioStream = findAudioStream,
                    findVideoStream = findVideoStream,
                    hardwareAcceleration = hardwareAcceleration
                ),
                media = media
            )
        }

        internal fun createAudioDecoder(location: String): Result<Decoder<Media.Audio, Frame.Audio>> = probe(
            location = location,
            findAudioStream = true,
            findVideoStream = false,
            hardwareAcceleration = HardwareAcceleration.NONE
        ).mapCatching<Decoder<Media.Audio, Frame.Audio>, Media> { media ->
            when (media) {
                is Media.AudioVideo -> AudioDecoder(
                    decoder = NativeDecoder(
                        location = location,
                        findAudioStream = true,
                        findVideoStream = false,
                        hardwareAcceleration = HardwareAcceleration.NONE
                    ),
                    media = media.toAudio()
                )

                is Media.Audio -> AudioDecoder(
                    decoder = NativeDecoder(
                        location = location,
                        findAudioStream = true,
                        findVideoStream = false,
                        hardwareAcceleration = HardwareAcceleration.NONE
                    ),
                    media = media
                )

                is Media.Video -> throw Exception("Unable to create audio decoder")
            }
        }

        internal fun createVideoDecoder(
            location: String,
            hardwareAcceleration: HardwareAcceleration,
        ): Result<Decoder<Media.Video, Frame.Video>> = probe(
            location = location,
            findAudioStream = false,
            findVideoStream = true,
            hardwareAcceleration = hardwareAcceleration
        ).mapCatching { media ->
            when (media) {
                is Media.AudioVideo -> VideoDecoder(
                    decoder = NativeDecoder(
                        location = location,
                        findAudioStream = false,
                        findVideoStream = true,
                        hardwareAcceleration = hardwareAcceleration
                    ),
                    media = media.toVideo()
                )

                is Media.Audio -> throw Exception("Unable to create video decoder")

                is Media.Video -> VideoDecoder(
                    decoder = NativeDecoder(
                        location = location,
                        findAudioStream = false,
                        findVideoStream = true,
                        hardwareAcceleration = hardwareAcceleration
                    ),
                    media = media
                )
            }
        }

        internal fun getSupportedHardwareAcceleration() = runCatching {
            NativeDecoder.getSupportedHardwareAcceleration().map { hardwareAcceleration ->
                HardwareAcceleration.entries.getOrNull(hardwareAcceleration)
            }.filterNotNull()
        }
    }
}