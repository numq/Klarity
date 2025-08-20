package io.github.numq.klarity.pipeline

import io.github.numq.klarity.buffer.Buffer
import io.github.numq.klarity.decoder.Decoder
import io.github.numq.klarity.format.Format
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pool.Pool
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
        override suspend fun close() =
            listOf(sampler.close(), buffer.close(), decoder.close()).fold(Result.success(Unit)) { acc, result ->
                acc.fold(onSuccess = { result }, onFailure = { acc })
            }
    }

    internal data class VideoPipeline(
        val decoder: Decoder<Format.Video>, val pool: Pool<Data>, val buffer: Buffer<Frame>
    ) : CloseablePipeline {
        override suspend fun close() =
            listOf(buffer.close(), decoder.close(), pool.close()).fold(Result.success(Unit)) { acc, result ->
                acc.fold(onSuccess = { result }, onFailure = { acc })
            }
    }

    override suspend fun close() = runCatching {
        coroutineScope {
            listOfNotNull(
                audioPipeline?.let { async { it.close() } },
                videoPipeline?.let { async { it.close() } }).awaitAll().fold(Result.success(Unit)) { acc, result ->
                acc.fold(onSuccess = { result }, onFailure = { acc })
            }.getOrThrow()
        }
    }
}