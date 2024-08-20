package player

import command.Command
import controller.PlayerController
import settings.Settings

internal class DefaultKlarityPlayer(
    private val playerController: PlayerController,
) : KlarityPlayer {
    override val settings = playerController.settings

    override val state = playerController.state

    override val bufferTimestamp = playerController.bufferTimestamp

    override val playbackTimestamp = playerController.playbackTimestamp

    override val renderer = playerController.renderer

    override val events = playerController.events

    override suspend fun changeSettings(settings: Settings) = playerController.changeSettings(settings)

    override suspend fun resetSettings() = playerController.resetSettings()

    override suspend fun load(
        location: String,
        enableAudio: Boolean,
        enableVideo: Boolean,
    ) = playerController.execute(
        Command.Prepare(
            location = location,
            audioBufferSize = if (enableAudio) settings.value.audioBufferSize else 0,
            videoBufferSize = if (enableVideo) settings.value.videoBufferSize else 0
        )
    )

    override suspend fun unload() = playerController.execute(Command.Release)

    override suspend fun play() = playerController.execute(Command.Play)

    override suspend fun resume() = playerController.execute(Command.Resume)

    override suspend fun pause() = playerController.execute(Command.Pause)

    override suspend fun stop() = playerController.execute(Command.Stop)

    override suspend fun seekTo(millis: Long) = playerController.execute(Command.SeekTo(millis = millis))

    override fun close() = runCatching { playerController.close() }.getOrDefault(Unit)
}