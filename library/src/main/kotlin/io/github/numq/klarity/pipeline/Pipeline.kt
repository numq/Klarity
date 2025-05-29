package io.github.numq.klarity.pipeline

import io.github.numq.klarity.buffer.Buffer
import io.github.numq.klarity.decoder.Decoder
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pool.Pool
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.sampler.Sampler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.skia.Data

internal sealed interface Pipeline {
    val media: Media

    suspend fun close(): Result<Unit>

    data class Audio(
        override val media: Media.Audio,
        val decoder: Decoder<Media.Audio>,
        val buffer: Buffer<Frame>,
        val sampler: Sampler,
    ) : Pipeline {
        override suspend fun close() = runCatching {
            sampler.close().getOrThrow()

            buffer.close().getOrThrow()

            decoder.close().getOrThrow()
        }
    }

    data class Video(
        override val media: Media.Video,
        val decoder: Decoder<Media.Video>,
        val pool: Pool<Data>,
        val buffer: Buffer<Frame>,
        val renderer: Renderer
    ) : Pipeline {
        override suspend fun close() = runCatching {
            renderer.close().getOrThrow()

            buffer.close().getOrThrow()

            decoder.close().getOrThrow()

            pool.close().getOrThrow()
        }
    }

    data class AudioVideo(
        override val media: Media.AudioVideo,
        val audioDecoder: Decoder<Media.Audio>,
        val videoDecoder: Decoder<Media.Video>,
        val videoPool: Pool<Data>,
        val audioBuffer: Buffer<Frame>,
        val videoBuffer: Buffer<Frame>,
        val sampler: Sampler,
        val renderer: Renderer
    ) : Pipeline {
        override suspend fun close() = runCatching {
            coroutineScope {
                val audioJob = async {
                    sampler.close()

                    audioBuffer.close()

                    audioDecoder.close()
                }

                val videoJob = async {
                    renderer.close()

                    videoBuffer.close()

                    videoDecoder.close()

                    videoPool.close()
                }

                awaitAll(audioJob, videoJob).fold(Result.success(Unit)) { acc, result ->
                    acc.fold(onSuccess = { result }, onFailure = { acc })
                }.getOrThrow()
            }
        }
    }
}