package io.github.numq.example.playlist.presentation

import io.github.numq.example.feature.Reducer
import io.github.numq.example.feature.Transition
import io.github.numq.example.playback.ChangePlaybackSpeed
import io.github.numq.example.playback.ChangeVolume
import io.github.numq.example.playback.ToggleMute
import io.github.numq.example.playlist.ControlPlaylistPlayback

class PlaylistPlaybackReducer(
    private val toggleMute: ToggleMute,
    private val changeVolume: ChangeVolume,
    private val changePlaybackSpeed: ChangePlaybackSpeed,
    private val controlPlaylistPlayback: ControlPlaylistPlayback,
) : Reducer<PlaylistCommand.Playback, PlaylistState, PlaylistEvent> {
    override suspend fun reduce(
        state: PlaylistState, command: PlaylistCommand.Playback,
    ): Transition<PlaylistState, PlaylistEvent> = when (command) {
        is PlaylistCommand.Playback.Play -> controlPlaylistPlayback.execute(
            input = ControlPlaylistPlayback.PlaybackCommand.Play
        ).fold(onSuccess = {
            transition(state)
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not play media: ${it.message}"))
        })

        is PlaylistCommand.Playback.Pause -> controlPlaylistPlayback.execute(
            input = ControlPlaylistPlayback.PlaybackCommand.Pause
        ).fold(onSuccess = {
            transition(state)
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not pause media: ${it.message}"))
        })

        is PlaylistCommand.Playback.Resume -> controlPlaylistPlayback.execute(
            input = ControlPlaylistPlayback.PlaybackCommand.Resume
        ).fold(onSuccess = {
            transition(state)
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not resume media: ${it.message}"))
        })

        is PlaylistCommand.Playback.Stop -> controlPlaylistPlayback.execute(
            input = ControlPlaylistPlayback.PlaybackCommand.Stop
        ).fold(onSuccess = {
            transition(state)
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not stop media: ${it.message}"))
        })

        is PlaylistCommand.Playback.SeekTo -> controlPlaylistPlayback.execute(
            input = ControlPlaylistPlayback.PlaybackCommand.SeekTo(
                timestamp = command.timestamp, playbackState = state.playlist.playbackState
            )
        ).fold(onSuccess = {
            transition(state)
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not seek media to timestamp: ${it.message}"))
        })

        is PlaylistCommand.Playback.ToggleMute -> toggleMute.execute(Unit).fold(onSuccess = {
            transition(state)
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not toggle mute: ${it.message}"))
        })

        is PlaylistCommand.Playback.ChangeVolume -> changeVolume.execute(
            input = ChangeVolume.Input(volume = command.volume)
        ).fold(onSuccess = {
            transition(state)
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not change volume: ${it.message}"))
        })

        is PlaylistCommand.Playback.DecreaseSpeed -> changePlaybackSpeed.execute(
            input = ChangePlaybackSpeed.Input.Decrease
        ).fold(onSuccess = {
            transition(state)
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not decrease playback speed: ${it.message}"))
        })

        is PlaylistCommand.Playback.IncreaseSpeed -> changePlaybackSpeed.execute(
            input = ChangePlaybackSpeed.Input.Increase
        ).fold(onSuccess = {
            transition(state)
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not increase playback speed: ${it.message}"))
        })

        is PlaylistCommand.Playback.ResetSpeed -> changePlaybackSpeed.execute(
            input = ChangePlaybackSpeed.Input.Reset
        ).fold(onSuccess = {
            transition(state)
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not reset playback speed: ${it.message}"))
        })
    }
}