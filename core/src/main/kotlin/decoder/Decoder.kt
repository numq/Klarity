package decoder

import format.AudioFormat
import format.VideoFormat
import frame.Frame
import media.Location
import media.Media
import java.io.File
import java.util.*

interface Decoder<Frame> : AutoCloseable {
    val media: Media
    suspend fun nextFrame(): Result<Frame?>
    fun seekTo(micros: Long): Result<Unit>
    fun reset(): Result<Unit>

    companion object {
        private fun probe(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
        ): Result<Media> = NativeDecoder().runCatching {
            check(init(location, findAudioStream, findVideoStream)) { "Unable to open media" }

            val id = Objects.hash(id, location).toLong()

            val mediaLocation = File(location).takeIf(File::exists)?.run {
                Location.Local(fileName = name, path = path)
            } ?: Location.Remote(url = location)

            val audioFormat = runCatching {
                if (findAudioStream) {
                    check(format.sampleRate > 0 && format.channels > 0) { "Audio decoding is not supported by media" }

                    AudioFormat(sampleRate = format.sampleRate, channels = format.channels)
                } else null
            }.getOrNull()

            val videoFormat = runCatching {
                if (findVideoStream) {
                    check(format.width > 0 && format.height > 0 && format.frameRate >= 0) { "Video decoding is not supported by media" }

                    VideoFormat(width = format.width, height = format.height, frameRate = format.frameRate)
                } else null
            }.getOrNull()

            Media(
                id = id,
                durationMicros = format.durationMicros,
                location = mediaLocation,
                audioFormat = audioFormat,
                videoFormat = videoFormat
            )
        }

        internal fun createProbeDecoder(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
        ): Result<Decoder<Nothing>> = probe(
            location = location,
            findAudioStream = findAudioStream,
            findVideoStream = findVideoStream
        ).mapCatching { media ->
            NativeDecoder().apply {
                init(location, findAudioStream, findVideoStream)
            }.let { decoder ->
                ProbeDecoder(
                    decoder = decoder,
                    media = media
                )
            }
        }

        internal fun createAudioDecoder(location: String): Result<Decoder<Frame.Audio>> = probe(
            location = location,
            findAudioStream = true,
            findVideoStream = false
        ).mapCatching { media ->
            NativeDecoder().apply {
                init(location, true, false)
            }.let { decoder ->
                AudioDecoder(
                    decoder = decoder,
                    media = media
                )
            }
        }

        internal fun createVideoDecoder(location: String): Result<Decoder<Frame.Video>> = probe(
            location = location,
            findAudioStream = false,
            findVideoStream = true
        ).mapCatching { media ->
            NativeDecoder().apply {
                init(location, false, true)
            }.let { decoder ->
                VideoDecoder(
                    decoder = decoder,
                    media = media
                )
            }
        }
    }
}