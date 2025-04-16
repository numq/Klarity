package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.decoder.Decoder
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.media.Media
import com.github.numq.klarity.core.renderer.Renderer
import kotlinx.coroutines.*
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultPreviewManager(
    private val videoDecoder: Decoder<Media.Video, Frame.Video>,
) : PreviewManager {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    private var previewJob: Job? = null

    @Volatile
    private var renderer: Renderer? = null

    override fun attachRenderer(renderer: Renderer) {
        this.renderer = renderer
    }

    override suspend fun preview(
        timestampMillis: Long,
        debounceMillis: Long,
        keyframesOnly: Boolean,
    ) = runCatching {
        previewJob?.cancel()
        previewJob = coroutineScope.launch {
            videoDecoder.seekTo(
                micros = timestampMillis.milliseconds.inWholeMicroseconds, keyframesOnly = keyframesOnly
            ).getOrThrow()

            (videoDecoder.decode().getOrNull() as? Frame.Video.Content)?.let { frame ->
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