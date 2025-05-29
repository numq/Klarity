package io.github.numq.klarity.preview

import io.github.numq.klarity.decoder.Decoder
import io.github.numq.klarity.frame.Frame
import io.github.numq.klarity.media.Media
import io.github.numq.klarity.pool.Pool
import io.github.numq.klarity.renderer.Renderer
import kotlinx.coroutines.CancellationException
import org.jetbrains.skia.Data
import kotlin.time.Duration

internal class DefaultPreviewManager(
    private val decoder: Decoder<Media.Video>,
    private val pool: Pool<Data>,
) : PreviewManager {
    override val format = decoder.media.videoFormat

    override suspend fun preview(
        renderer: Renderer,
        timestamp: Duration,
        keyframesOnly: Boolean,
    ) = runCatching {
        check(timestamp in Duration.ZERO..decoder.media.duration) { "Preview timestamp is out of range" }

        if (timestamp.isPositive()) {
            decoder.seekTo(
                timestamp = timestamp.coerceIn(Duration.ZERO..decoder.media.duration),
                keyframesOnly = keyframesOnly
            ).getOrThrow()
        }

        val data = pool.acquire().getOrThrow()

        try {
            (decoder.decodeVideo(data = data).getOrThrow() as? Frame.Content.Video)?.let { frame ->
                renderer.render(frame).getOrThrow()
            }
        } finally {
            pool.release(item = data).getOrThrow()
        }

        Unit
    }.recoverCatching { t ->
        if (t !is CancellationException) {
            throw PreviewManagerException(t)
        }
    }

    override suspend fun close() = runCatching {
        decoder.close().getOrThrow()

        pool.close().getOrThrow()
    }
}