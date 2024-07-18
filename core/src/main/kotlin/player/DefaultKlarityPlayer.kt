package player

import command.Command
import controller.PlayerController
import event.Event
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.*
import pipeline.Pipeline
import settings.Settings
import state.InternalState
import state.State

internal class DefaultKlarityPlayer(
    private val playerController: PlayerController,
) : KlarityPlayer {
    private val coroutineScope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    override val settings = playerController.settings

    override val state = playerController.internalState.map { internalState ->
        when (internalState) {
            is InternalState.Empty -> State.Empty

            is InternalState.Loaded -> with(internalState) {
                when (internalState) {
                    is InternalState.Loaded.Playing -> State.Loaded.Playing(media = media)

                    is InternalState.Loaded.Paused -> State.Loaded.Paused(media = media)

                    is InternalState.Loaded.Stopped -> State.Loaded.Stopped(media = media)

                    is InternalState.Loaded.Completed -> State.Loaded.Completed(media = media)

                    is InternalState.Loaded.Seeking -> State.Loaded.Seeking(media = media)
                }
            }
        }
    }.stateIn(scope = coroutineScope, started = SharingStarted.Eagerly, initialValue = State.Empty)

    override var renderer =
        playerController.internalState.filterIsInstance<InternalState.Loaded>().map { internalState ->
            when (val pipeline = internalState.pipeline) {
                is Pipeline.Media -> pipeline.renderer

                is Pipeline.Audio -> null

                is Pipeline.Video -> pipeline.renderer
            }
        }.stateIn(scope = coroutineScope, started = SharingStarted.Eagerly, initialValue = null)

    private val _events = MutableSharedFlow<Event>()

    override val events = merge(playerController.events, _events)

    override suspend fun changeSettings(settings: Settings) = playerController.changeSettings(settings)

    override suspend fun resetSettings() = playerController.resetSettings()

    override suspend fun load(
        location: String,
        enableAudio: Boolean,
        enableVideo: Boolean,
    ) {
        when (state.value) {
            is State.Empty -> playerController.prepare(
                location = location,
                audioBufferSize = if (enableAudio) settings.value.audioBufferSize else 0,
                videoBufferSize = if (enableVideo) settings.value.videoBufferSize else 0
            )

            is State.Loaded -> Unit
        }
    }

    override suspend fun unload() {
        when (state.value) {
            is State.Empty -> Unit

            is State.Loaded -> playerController.release()
        }
    }

    override suspend fun play() {
        when (state.value) {
            is State.Empty,
            is State.Loaded.Seeking,
            -> _events.emit(Event.Error(Exception("Unable to play")))

            is State.Loaded.Playing -> Unit

            is State.Loaded.Paused -> playerController.execute(Command.Resume)

            is State.Loaded.Stopped -> playerController.execute(Command.Play)

            is State.Loaded.Completed -> {
                playerController.execute(Command.Stop)
                playerController.execute(Command.Play)
            }
        }
    }

    override suspend fun pause() {
        when (state.value) {
            is State.Empty,
            is State.Loaded.Stopped,
            is State.Loaded.Completed,
            is State.Loaded.Seeking,
            -> _events.emit(Event.Error(Exception("Unable to pause")))

            is State.Loaded.Paused -> Unit

            else -> playerController.execute(Command.Pause)
        }
    }

    override suspend fun stop() {
        when (state.value) {
            is State.Empty -> _events.emit(Event.Error(Exception("Unable to stop")))

            is State.Loaded.Stopped -> Unit

            else -> playerController.execute(Command.Stop)
        }
    }

    override suspend fun seekTo(millis: Long) {
        when (state.value) {
            is State.Empty -> _events.emit(Event.Error(Exception("Unable to seek")))

            is State.Loaded.Seeking -> Unit

            else -> playerController.execute(Command.SeekTo(millis = millis))
        }
    }

    override fun close() = runCatching {
        coroutineScope.cancel()
        playerController.close()
    }.getOrDefault(Unit)
}