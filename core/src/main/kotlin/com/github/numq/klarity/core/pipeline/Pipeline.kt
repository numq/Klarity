package com.github.numq.klarity.core.pipeline

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.sampler.Sampler

sealed interface Pipeline {
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
            decoder.close().getOrThrow()
        }
    }

    data class Video(
        override val media: Media.Video,
        val decoder: Decoder<Media.Video>,
        val buffer: Buffer<Frame>
    ) : Pipeline {
        override suspend fun close() = runCatching {
            decoder.close().getOrThrow()
        }
    }

    data class AudioVideo(
        override val media: Media.AudioVideo,
        val audioDecoder: Decoder<Media.Audio>,
        val videoDecoder: Decoder<Media.Video>,
        val audioBuffer: Buffer<Frame>,
        val videoBuffer: Buffer<Frame>,
        val sampler: Sampler
    ) : Pipeline {
        override suspend fun close() = runCatching {
            sampler.close().getOrThrow()
            audioDecoder.close().getOrThrow()
            videoDecoder.close().getOrThrow()
        }
    }
}