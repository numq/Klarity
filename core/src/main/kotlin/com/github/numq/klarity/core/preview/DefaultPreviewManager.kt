package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.frame.Frame
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultPreviewManager(private val videoDecoderFactory: VideoDecoderFactory) : PreviewManager {
    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    private val coroutineScope = CoroutineScope(coroutineContext)

    private val internalState = MutableStateFlow<InternalPreviewState>(InternalPreviewState.Empty)

    override val state = internalState.map { internalState ->
        when (internalState) {
            is InternalPreviewState.Empty -> PreviewState.Empty

            is InternalPreviewState.Ready -> PreviewState.Ready(media = internalState.decoder.media)
        }
    }.stateIn(scope = coroutineScope, started = SharingStarted.Lazily, initialValue = PreviewState.Empty)

    override suspend fun prepare(location: String) = runCatching {
        val currentState = internalState.value

        check(currentState is InternalPreviewState.Empty) { "Unable to load non-empty preview manager" }

        videoDecoderFactory.create(
            parameters = VideoDecoderFactory.Parameters(location = location)
        ).mapCatching { decoder ->
            internalState.emit(InternalPreviewState.Ready(decoder = decoder))
        }.getOrThrow()
    }

    override suspend fun preview(
        timestampMillis: Long,
        width: Int?,
        height: Int?,
        keyframesOnly: Boolean,
    ): Result<Frame.Video.Content?> = runCatching {
        val currentState = internalState.value

        check(currentState is InternalPreviewState.Ready) { "Unable to use empty preview manager" }

        with(currentState.decoder) {
            seekTo(
                micros = timestampMillis.milliseconds.inWholeMicroseconds,
                keyframesOnly = keyframesOnly
            ).map {
                nextFrame(width, height).getOrNull() as? Frame.Video.Content
            }.getOrThrow()
        }
    }

    override suspend fun release() = runCatching {
        val currentState = internalState.value

        check(currentState is InternalPreviewState.Ready) { "Unable to unload empty preview manager" }

        currentState.decoder.close()

        internalState.emit(InternalPreviewState.Empty)
    }

    override fun close() {
        coroutineContext.cancelChildren()

        when (val internalState = internalState.value) {
            is InternalPreviewState.Empty -> Unit

            is InternalPreviewState.Ready -> internalState.decoder.close()
        }
    }
}