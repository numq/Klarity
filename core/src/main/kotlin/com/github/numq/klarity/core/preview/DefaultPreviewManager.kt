package com.github.numq.klarity.core.preview

import com.github.numq.klarity.core.decoder.VideoDecoderFactory
import com.github.numq.klarity.core.frame.Frame
import com.github.numq.klarity.core.hwaccel.HardwareAcceleration
import com.github.numq.klarity.core.hwaccel.HardwareAccelerationFallback
import kotlinx.coroutines.flow.MutableStateFlow
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

internal class DefaultPreviewManager(
    private val videoDecoderFactory: VideoDecoderFactory,
) : PreviewManager {
    private val internalState = AtomicReference<InternalPreviewState>(InternalPreviewState.Empty)

    override val state = MutableStateFlow<PreviewState>(PreviewState.Empty)

    override suspend fun prepare(
        location: String,
        hardwareAcceleration: HardwareAcceleration,
        hardwareAccelerationFallback: HardwareAccelerationFallback,
    ) = runCatching {
        val currentState = internalState.get()

        check(currentState is InternalPreviewState.Empty) { "Unable to load non-empty preview manager" }

        videoDecoderFactory.create(
            parameters = VideoDecoderFactory.Parameters(
                location = location,
                hardwareAcceleration = hardwareAcceleration,
                hardwareAccelerationFallback = hardwareAccelerationFallback
            )
        ).mapCatching { decoder ->
            internalState.set(InternalPreviewState.Ready(decoder = decoder))

            state.emit(PreviewState.Ready(media = decoder.media))
        }.getOrThrow()
    }.recoverCatching { t ->
        throw PreviewException(t)
    }

    override suspend fun preview(
        timestampMillis: Long,
        width: Int?,
        height: Int?,
        keyframesOnly: Boolean,
    ): Result<Frame.Video.Content?> = runCatching {
        val currentState = internalState.get()

        check(currentState is InternalPreviewState.Ready) { "Unable to use empty preview manager" }

        with(currentState.decoder) {
            seekTo(
                micros = timestampMillis.milliseconds.inWholeMicroseconds, keyframesOnly = keyframesOnly
            ).map {
                decode(width, height).getOrNull() as? Frame.Video.Content
            }.getOrNull()
        }
    }.recoverCatching { t ->
        throw PreviewException(t)
    }

    override suspend fun release() = runCatching {
        val currentState = internalState.get()

        if (currentState is InternalPreviewState.Ready) {
            currentState.decoder.close().getOrThrow()

            internalState.set(InternalPreviewState.Empty)

            state.emit(PreviewState.Empty)
        }
    }.recoverCatching { t ->
        throw PreviewException(t)
    }

    override suspend fun close() = runCatching {
        when (val internalState = internalState.get()) {
            is InternalPreviewState.Empty -> Unit

            is InternalPreviewState.Ready -> internalState.decoder.close().getOrThrow()
        }
    }
}