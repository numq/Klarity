package io.github.numq.klarity.player

import io.github.numq.klarity.command.Command
import io.github.numq.klarity.controller.PlayerController
import io.github.numq.klarity.hwaccel.HardwareAcceleration
import io.github.numq.klarity.renderer.Renderer
import io.github.numq.klarity.settings.PlayerSettings
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

internal class DefaultKlarityPlayer(
    private val playerController: PlayerController,
) : KlarityPlayer {
    override val settings = playerController.settings

    override val state = playerController.state

    override val bufferTimestamp = playerController.bufferTimestamp

    override val playbackTimestamp = playerController.playbackTimestamp

    override val events = playerController.events

    override suspend fun attachRenderer(renderer: Renderer) = playerController.attachRenderer(renderer = renderer)

    override suspend fun detachRenderer() = playerController.detachRenderer()

    override suspend fun changeSettings(
        settings: PlayerSettings,
    ) = playerController.changeSettings(settings).recoverCatching { t ->
        if (t !is CancellationException) {
            throw KlarityPlayerException(t)
        }
    }

    override suspend fun resetSettings() = playerController.resetSettings().recoverCatching { t ->
        if (t !is CancellationException) {
            throw KlarityPlayerException(t)
        }
    }

    override suspend fun prepare(
        location: String,
        audioBufferSize: Int,
        videoBufferSize: Int,
        hardwareAccelerationCandidates: List<HardwareAcceleration>?,
    ) = playerController.execute(
        Command.Prepare(
            location = location,
            audioBufferSize = audioBufferSize,
            videoBufferSize = videoBufferSize,
            hardwareAccelerationCandidates = hardwareAccelerationCandidates
        )
    ).recoverCatching { t ->
        if (t !is CancellationException) {
            throw KlarityPlayerException(t)
        }
    }

    override suspend fun play() = playerController.execute(Command.Play).recoverCatching { t ->
        if (t !is CancellationException) {
            throw KlarityPlayerException(t)
        }
    }

    override suspend fun resume() = playerController.execute(Command.Resume).recoverCatching { t ->
        if (t !is CancellationException) {
            throw KlarityPlayerException(t)
        }
    }

    override suspend fun pause() = playerController.execute(Command.Pause).recoverCatching { t ->
        if (t !is CancellationException) {
            throw KlarityPlayerException(t)
        }
    }

    override suspend fun stop() = playerController.execute(Command.Stop).recoverCatching { t ->
        if (t !is CancellationException) {
            throw KlarityPlayerException(t)
        }
    }

    override suspend fun seekTo(timestamp: Duration, keyFramesOnly: Boolean) = playerController.execute(
        Command.SeekTo(timestamp = timestamp, keyFramesOnly = keyFramesOnly)
    ).recoverCatching { t ->
        if (t !is CancellationException) {
            throw KlarityPlayerException(t)
        }
    }

    override suspend fun release() = playerController.execute(Command.Release).recoverCatching { t ->
        if (t !is CancellationException) {
            throw KlarityPlayerException(t)
        }
    }

    override suspend fun close() = playerController.close().recoverCatching { t ->
        if (t !is CancellationException) {
            throw KlarityPlayerException(t)
        }
    }
}