package com.github.numq.klarity.core.player

import com.github.numq.klarity.core.command.Command
import com.github.numq.klarity.core.controller.PlayerController
import com.github.numq.klarity.core.renderer.Renderer
import com.github.numq.klarity.core.settings.AudioSettings
import com.github.numq.klarity.core.settings.PlayerSettings
import com.github.numq.klarity.core.settings.VideoSettings
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

    override fun attachRenderer(renderer: Renderer) = playerController.attachRenderer(renderer)

    override fun detachRenderer() = playerController.detachRenderer()

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
        enableAudio: Boolean,
        enableVideo: Boolean,
        audioSettings: AudioSettings,
        videoSettings: VideoSettings,
    ) = playerController.execute(
        Command.Prepare(
            location = location,
            audioBufferSize = if (enableAudio) settings.value.audioBufferSize else 0,
            videoBufferSize = if (enableVideo) settings.value.videoBufferSize else 0,
            sampleRate = audioSettings.sampleRate,
            channels = audioSettings.channels,
            width = videoSettings.width,
            height = videoSettings.height,
            frameRate = videoSettings.frameRate,
            hardwareAccelerationCandidates = videoSettings.hardwareAccelerationCandidates
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