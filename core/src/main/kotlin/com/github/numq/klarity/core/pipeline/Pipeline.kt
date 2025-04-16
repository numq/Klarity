package com.github.numq.klarity.core.pipeline

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.sampler.Sampler

sealed interface Pipeline {
    val media: Media

    suspend fun close(): Result<Unit>

    data class AudioVideo(
        override val media: Media.AudioVideo,
        val audioDecoder: Decoder<Media.Audio, Frame.Audio>,
        val videoDecoder: Decoder<Media.Video, Frame.Video>,
        val audioBuffer: Buffer<Frame.Audio>,
        val videoBuffer: Buffer<Frame.Video>,
        val sampler: Sampler
    ) : Pipeline {
        override suspend fun close() = runCatching {
            sampler.close().getOrThrow()
            audioBuffer.close().getOrThrow()
            videoBuffer.close().getOrThrow()
            audioDecoder.close().getOrThrow()
            videoDecoder.close().getOrThrow()
        }
    }

    data class Audio(
        override val media: Media.Audio,
        val decoder: Decoder<Media.Audio, Frame.Audio>,
        val buffer: Buffer<Frame.Audio>,
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
        val decoder: Decoder<Media.Video, Frame.Video>,
        val buffer: Buffer<Frame.Video>
    ) : Pipeline {
        override suspend fun close() = runCatching {
            buffer.close().getOrThrow()
            decoder.close().getOrThrow()
        }
    }
}