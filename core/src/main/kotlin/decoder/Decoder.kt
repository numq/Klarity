package decoder

import format.AudioFormat
import format.VideoFormat
import frame.Frame
import media.Location
import media.Media
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
        ): Result<Media> = runCatching {
            val mediaLocation = File(location).takeIf(File::exists)?.run {
                Location.Local(fileName = name, path = path)
            } ?: URI.create(location).takeIf(URI::isAbsolute)?.run {
                Location.Remote(url = location)
            }

            checkNotNull(mediaLocation) { "Unable to find media" }

            val media: Media

            NativeDecoder(location, findAudioStream, findVideoStream).use { decoder ->
                val format = decoder.format

                val audioFormat = runCatching {
                    if (findAudioStream) {
                        check(format.sampleRate() > 0 && format.channels() > 0) { "Audio decoding is not supported by media" }

                        AudioFormat(sampleRate = format.sampleRate(), channels = format.channels())
                    } else null
                }.getOrNull()

                val videoFormat = runCatching {
                    if (findVideoStream) {
                        check(format.width() > 0 && format.height() > 0 && format.frameRate() >= 0) { "Video decoding is not supported by media" }

                        VideoFormat(
                            width = format.width(), height = format.height(), frameRate = format.frameRate()
                        )
                    } else null
                }.getOrNull()

                media = when {
                    audioFormat != null && videoFormat != null -> Media.AudioVideo(
                        id = decoder.hashCode().toLong(),
                        durationMicros = format.durationMicros(),
                        location = mediaLocation,
                        audioFormat = audioFormat,
                        videoFormat = videoFormat
                    )

                    audioFormat != null -> Media.Audio(
                        id = decoder.hashCode().toLong(),
                        durationMicros = format.durationMicros(),
                        location = mediaLocation,
                        format = audioFormat,
                    )

                    videoFormat != null -> Media.Video(
                        id = decoder.hashCode().toLong(),
                        durationMicros = format.durationMicros(),
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
        ): Result<Decoder<Media, Frame.Probe>> = probe(
            location = location, findAudioStream = findAudioStream, findVideoStream = findVideoStream
        ).mapCatching { media ->
            ProbeDecoder(
                decoder = NativeDecoder(location, findAudioStream, findVideoStream), media = media
            )
        }

        internal fun createAudioDecoder(location: String): Result<Decoder<Media.Audio, Frame.Audio>> = probe(
            location = location, findAudioStream = true, findVideoStream = false
        ).mapCatching<Decoder<Media.Audio, Frame.Audio>, Media> { media ->
            when (media) {
                is Media.AudioVideo -> AudioDecoder(
                    decoder = NativeDecoder(location, true, false), media = media.toAudio()
                )

                is Media.Audio -> AudioDecoder(
                    decoder = NativeDecoder(location, true, false), media = media
                )

                is Media.Video -> throw Exception("Unable to create audio decoder")
            }
        }

        internal fun createVideoDecoder(location: String): Result<Decoder<Media.Video, Frame.Video>> = probe(
            location = location, findAudioStream = false, findVideoStream = true
        ).mapCatching { media ->
            when (media) {
                is Media.AudioVideo -> VideoDecoder(
                    decoder = NativeDecoder(location, false, true), media = media.toVideo()
                )

                is Media.Audio -> throw Exception("Unable to create video decoder")

                is Media.Video -> VideoDecoder(
                    decoder = NativeDecoder(location, false, true), media = media
                )
            }
        }
    }
}