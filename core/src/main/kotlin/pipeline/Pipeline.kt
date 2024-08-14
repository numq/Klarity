package pipeline

import buffer.Buffer
import decoder.Decoder
import frame.Frame
import renderer.Renderer
import sampler.Sampler

sealed interface Pipeline : AutoCloseable {
    val media: media.Media

    data class Media(
        override val media: media.Media,
        val audioDecoder: Decoder<Frame.Audio>,
        val videoDecoder: Decoder<Frame.Video>,
        val audioBuffer: Buffer<Frame.Audio>,
        val videoBuffer: Buffer<Frame.Video>,
        val sampler: Sampler,
        val renderer: Renderer,
    ) : Pipeline {
        override fun close() {
            audioDecoder.close()
            videoDecoder.close()
            sampler.close()
            renderer.close()
        }
    }

    data class Audio(
        override val media: media.Media,
        val decoder: Decoder<Frame.Audio>,
        val buffer: Buffer<Frame.Audio>,
        val sampler: Sampler,
    ) : Pipeline {
        override fun close() {
            decoder.close()
            sampler.close()
        }
    }

    data class Video(
        override val media: media.Media,
        val decoder: Decoder<Frame.Video>,
        val buffer: Buffer<Frame.Video>,
        val renderer: Renderer,
    ) : Pipeline {
        override fun close() {
            decoder.close()
            renderer.close()
        }
    }
}