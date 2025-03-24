package com.github.numq.klarity.core.player

import com.github.numq.klarity.core.command.Command
import com.github.numq.klarity.core.controller.PlayerController
import com.github.numq.klarity.core.decoder.HardwareAcceleration
import com.github.numq.klarity.core.settings.PlayerSettings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.withContext

internal class DefaultKlarityPlayer(
    private val playerController: PlayerController,
) : KlarityPlayer {
    private val coroutineContext = Dispatchers.Default + SupervisorJob()

    override val settings = playerController.settings

    override val state = playerController.state

    override val bufferTimestamp = playerController.bufferTimestamp

    override val playbackTimestamp = playerController.playbackTimestamp

    override val renderer = playerController.renderer

    override val events = playerController.events

    override suspend fun changeSettings(settings: PlayerSettings) = playerController.changeSettings(settings)

    override suspend fun resetSettings() = playerController.resetSettings()

    override suspend fun prepare(
        location: String,
        enableAudio: Boolean,
        enableVideo: Boolean,
        hardwareAcceleration: HardwareAcceleration,
    ) = withContext(coroutineContext) {
        playerController.execute(
            Command.Prepare(
                location = location,
                audioBufferSize = if (enableAudio) settings.value.audioBufferSize else 0,
                videoBufferSize = if (enableVideo) settings.value.videoBufferSize else 0,
                hardwareAcceleration = hardwareAcceleration
            )
        )
    }

    override suspend fun play() = withContext(coroutineContext) {
        playerController.execute(Command.Play)
    }

    override suspend fun resume() = withContext(coroutineContext) {
        playerController.execute(Command.Resume)
    }

    override suspend fun pause() = withContext(coroutineContext) {
        playerController.execute(Command.Pause)
    }

    override suspend fun stop() = withContext(coroutineContext) {
        playerController.execute(Command.Stop)
    }

    override suspend fun seekTo(millis: Long) = withContext(coroutineContext) {
        playerController.execute(Command.SeekTo(millis = millis))
    }

    override suspend fun release() = withContext(coroutineContext) {
        playerController.execute(Command.Release)
    }

    override fun close() {
        coroutineContext.cancelChildren()

        playerController.close()
    }
}