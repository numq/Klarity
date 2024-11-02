package com.github.numq.klarity.core.pipeline

import com.github.numq.klarity.core.buffer.Buffer
import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.sampler.Sampler

sealed interface Pipeline : AutoCloseable {
    val media: Media

    data class AudioVideo(
        override val media: Media,
        val audioDecoder: Decoder<Media.Audio, Frame.Audio>,
        val videoDecoder: Decoder<Media.Video, Frame.Video>,
        val audioBuffer: Buffer<Frame.Audio>,
        val videoBuffer: Buffer<Frame.Video>,
        val sampler: Sampler,
        val renderer: Renderer,
    ) : Pipeline {
        override fun close() {
            sampler.close()
            audioDecoder.close()
            videoDecoder.close()
        }
    }

    data class Audio(
        override val media: Media,
        val decoder: Decoder<Media.Audio, Frame.Audio>,
        val buffer: Buffer<Frame.Audio>,
        val sampler: Sampler,
    ) : Pipeline {
        override fun close() {
            sampler.close()
            decoder.close()
        }
    }

    data class Video(
        override val media: Media,
        val decoder: Decoder<Media.Video, Frame.Video>,
        val buffer: Buffer<Frame.Video>,
        val renderer: Renderer,
    ) : Pipeline {
        override fun close() {
            decoder.close()
        }
    }
}