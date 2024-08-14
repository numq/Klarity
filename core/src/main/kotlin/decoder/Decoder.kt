package decoder

import format.AudioFormat
import format.VideoFormat
import frame.Frame
import media.Location
import media.Media
import java.io.File
import java.net.URI

interface Decoder<Frame> : AutoCloseable {
    val media: Media
    suspend fun nextFrame(): Result<Frame>
    fun seekTo(micros: Long): Result<Unit>
    fun reset(): Result<Unit>

    companion object {
        private fun probe(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
        ): Result<Media> = runCatching {
            val decoder = NativeDecoder()

            val media: Media

            try {
                decoder.init(location, findAudioStream, findVideoStream)

                val format = decoder.format

                val mediaLocation = File(location).takeIf(File::exists)?.run {
                    Location.Local(fileName = name, path = path)
                } ?: URI.create(location).takeIf(URI::isAbsolute)?.run {
                    Location.Remote(url = location)
                }

                checkNotNull(mediaLocation) { "Unable to find media" }

                val audioFormat = runCatching {
                    if (findAudioStream) {
                        check(format.sampleRate > 0 && format.channels > 0) { "Audio decoding is not supported by media" }

                        AudioFormat(sampleRate = format.sampleRate, channels = format.channels)
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

                check(!(audioFormat == null && videoFormat == null)) { "Unsupported media format" }

                media = Media(
                    id = decoder.id,
                    durationMicros = format.durationMicros,
                    location = mediaLocation,
                    audioFormat = audioFormat,
                    videoFormat = videoFormat
                )
            } catch (t: Throwable) {
                throw t
            } finally {
                decoder.close()
            }

            media
        }.onFailure { println(it) }

        internal fun createProbeDecoder(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
        ): Result<Decoder<Unit>> = probe(
            location = location, findAudioStream = findAudioStream, findVideoStream = findVideoStream
        ).mapCatching { media ->
            ProbeDecoder(media = media)
        }

        internal fun createAudioDecoder(location: String): Result<Decoder<Frame.Audio>> = probe(
            location = location, findAudioStream = true, findVideoStream = false
        ).mapCatching { media ->
            checkNotNull(media.audioFormat) { "Unable to create audio decoder" }

            NativeDecoder().apply {
                init(location, true, false)
            }.let { decoder ->
                AudioDecoder(
                    decoder = decoder, media = media
                )
            }
        }

        internal fun createVideoDecoder(location: String): Result<Decoder<Frame.Video>> = probe(
            location = location, findAudioStream = false, findVideoStream = true
        ).mapCatching { media ->
            checkNotNull(media.videoFormat) { "Unable to create video decoder" }

            NativeDecoder().apply {
                init(location, false, true)
            }.let { decoder ->
                VideoDecoder(
                    decoder = decoder, media = media
                )
            }
        }
    }
}