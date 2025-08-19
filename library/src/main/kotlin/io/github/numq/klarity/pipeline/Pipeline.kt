package io.github.numq.klarity.pipeline

import io.github.numq.klarity.buffer.Buffer
import io.github.numq.klarity.decoder.Decoder
import io.github.numq.klarity.format.Format
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pool.Pool
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.sampler.Sampler
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.jetbrains.skia.Data

internal data class Pipeline(
    val media: Media, val audioPipeline: AudioPipeline?, val videoPipeline: VideoPipeline?
) : CloseablePipeline {
    internal data class AudioPipeline(
        val decoder: Decoder<Format.Audio>, val buffer: Buffer<Frame>, val sampler: Sampler
    ) : CloseablePipeline {
        override suspend fun close() = listOf(
            suspend { sampler.close() },
            suspend { buffer.close() },
            suspend { decoder.close() }).fold(Result.success(Unit)) { acc, op ->
            acc.fold(onSuccess = { op() }, onFailure = { acc })
        }
    }

    internal data class VideoPipeline(
        val decoder: Decoder<Format.Video>, val pool: Pool<Data>, val buffer: Buffer<Frame>, val renderer: Renderer
    ) : CloseablePipeline {
        override suspend fun close() = listOf(
            suspend { renderer.close() },
            suspend { buffer.close() },
            suspend { decoder.close() },
            suspend { pool.close() }).fold(Result.success(Unit)) { acc, op ->
            acc.fold(onSuccess = { op() }, onFailure = { acc })
        }
    }

    override suspend fun close() = runCatching {
        coroutineScope {
            listOfNotNull(
                audioPipeline?.let { pipeline -> async { pipeline.close() } },
                videoPipeline?.let { pipeline -> async { pipeline.close() } }).awaitAll().forEach { result ->
                result.getOrThrow()
            }
        }
    }
}