package decoder

import format.AudioFormat
import format.VideoFormat
import frame.Frame
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import media.Location
import media.Media
import java.io.File
import java.net.URI

interface Decoder<Frame> : AutoCloseable {
    val media: Media
    suspend fun nextFrame(): Result<Frame>
    suspend fun seekTo(micros: Long, keyframesOnly: Boolean): Result<Unit>
    suspend fun reset(): Result<Unit>

    companion object {
        private val coroutineContext = Dispatchers.Default + SupervisorJob()

        private fun probe(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
        ): Result<Media> = runCatching {
            val decoder = NativeDecoder(location, findAudioStream, findVideoStream)

            val media: Media

            try {
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
                            width = format.width, height = format.height, frameRate = format.frameRate
                        )
                    } else null
                }.getOrNull()

                check(!(audioFormat == null && videoFormat == null)) { "Unsupported media format" }

                media = Media(
                    id = decoder.hashCode().toLong(),
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
        }

        internal suspend fun createProbeDecoder(
            location: String,
            findAudioStream: Boolean,
            findVideoStream: Boolean,
        ): Result<Decoder<Unit>> = withContext(coroutineContext) {
            probe(
                location = location, findAudioStream = findAudioStream, findVideoStream = findVideoStream
            ).mapCatching { media ->
                ProbeDecoder(media = media)
            }
        }

        internal suspend fun createAudioDecoder(location: String): Result<Decoder<Frame.Audio>> =
            withContext(coroutineContext) {
                probe(
                    location = location, findAudioStream = true, findVideoStream = false
                ).mapCatching { media ->
                    checkNotNull(media.audioFormat) { "Unable to create audio decoder" }

                    AudioDecoder(
                        decoder = NativeDecoder(location, true, false),
                        media = media
                    )
                }
            }

        internal suspend fun createVideoDecoder(location: String): Result<Decoder<Frame.Video>> =
            withContext(coroutineContext) {
                probe(
                    location = location, findAudioStream = false, findVideoStream = true
                ).mapCatching { media ->
                    checkNotNull(media.videoFormat) { "Unable to create video decoder" }

                    VideoDecoder(
                        decoder = NativeDecoder(location, false, true),
                        media = media
                    )
                }
            }
    }
}