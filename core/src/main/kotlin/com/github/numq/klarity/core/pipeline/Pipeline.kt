package com.github.numq.klarity.core.pipeline

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.data.Data
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.pool.Pool
import com.github.numq.klarity.core.sampler.Sampler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

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
        val buffer: Buffer<Frame>
    ) : Pipeline {
        override suspend fun close() = runCatching {
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
        val sampler: Sampler
    ) : Pipeline {
        override suspend fun close() = runCatching {
            coroutineScope {
                val audioJob = async {
                    sampler.close()

                    audioBuffer.close()

                    audioDecoder.close()
                }

                val videoJob = async {
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