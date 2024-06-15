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
        ): Result<Media> = runCatching {
            val decoder = NativeDecoder()

            val media = try {
                decoder.takeIf { decoder.init(location, findAudioStream, findVideoStream) }?.format?.run {
                    val id = Objects.hash(decoder.id, location).toLong()

                    val mediaLocation = File(location).takeIf(File::exists)?.run {
                        Location.Local(fileName = name, path = path)
                    } ?: Location.Remote(url = location)

                    val audioFormat = runCatching {
                        if (findAudioStream) {
                            check(sampleRate > 0 && channels > 0) { "Audio decoding is not supported by media" }

                            AudioFormat(sampleRate = sampleRate, channels = channels)
                        } else null
                    }.getOrNull()

                    val videoFormat = runCatching {
                        if (findVideoStream) {
                            check(width > 0 && height > 0 && frameRate >= 0) { "Video decoding is not supported by media" }

                            VideoFormat(width = width, height = height, frameRate = frameRate)
                        } else null
                    }.getOrNull()

                    Media(
                        id = id,
                        durationMicros = durationMicros,
                        location = mediaLocation,
                        audioFormat = audioFormat,
                        videoFormat = videoFormat
                    )
                }
            } catch (e: Exception) {
                throw e
            } finally {
                decoder.close()
            }

            checkNotNull(media) { "Unable to open media" }
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