package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.renderer.Renderer
import kotlinx.coroutines.*
import kotlin.time.Duration

internal class DefaultPreviewManager(
    private val videoDecoder: Decoder<Media.Video>,
) : PreviewManager {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var previewJob: Job? = null

    @Volatile
    private var renderer: Renderer? = null

    override fun attachRenderer(renderer: Renderer) {
        this.renderer = renderer
    }

    override fun detachRenderer() {
        renderer = null
    }

    override suspend fun preview(
        timestamp: Duration,
        debounceTime: Duration,
        keyframesOnly: Boolean,
    ) = runCatching {
        previewJob?.cancel()
        previewJob = coroutineScope.launch {
            delay(debounceTime)

            videoDecoder.seekTo(
                timestamp = timestamp.coerceIn(Duration.ZERO..videoDecoder.media.duration),
                keyframesOnly = keyframesOnly
            ).getOrDefault(Unit)

            (videoDecoder.decode().getOrThrow() as? Frame.Content.Video)?.let { frame ->
                renderer?.render(frame)
            }
        }
    }.recoverCatching { t ->
        if (t !is CancellationException) {
            throw PreviewManagerException(t)
        }
    }

    override suspend fun close() = runCatching {
        coroutineScope.cancel()

        videoDecoder.close().getOrThrow()
    }
}