package io.github.numq.example.hub.presentation

import io.github.numq.example.feature.Reducer
import io.github.numq.example.feature.Transition
import io.github.numq.example.hub.StartHubPlayback
import io.github.numq.example.hub.StopHubPlayback
import io.github.numq.example.playback.ChangePlaybackSpeed

class HubPlaybackReducer(
    private val changePlaybackSpeed: ChangePlaybackSpeed,
    private val startHubPlayback: StartHubPlayback,
    private val stopHubPlayback: StopHubPlayback,
) : Reducer<HubCommand.Playback, HubState, HubEvent> {
    override suspend fun reduce(state: HubState, command: HubCommand.Playback): Transition<HubState, HubEvent> =
        when (command) {
            is HubCommand.Playback.StartPlayback -> startHubPlayback.execute(
                input = StartHubPlayback.Input(
                    item = command.item,
                    previewItem = state.hub.previewItem,
                    playbackItem = state.hub.playbackItem
                )
            ).fold(onSuccess = {
                transition(state)
            }, onFailure = {
                transition(state, HubEvent.Error("Could not start playback: ${it.message}"))
            })

            is HubCommand.Playback.StopPlayback -> stopHubPlayback.execute(
                input = StopHubPlayback.Input(item = command.item, playbackItem = state.hub.playbackItem)
            ).fold(onSuccess = {
                transition(state)
            }, onFailure = {
                transition(state, HubEvent.Error("Could not stop playback: ${it.message}"))
            })

            is HubCommand.Playback.DecreasePlaybackSpeed -> changePlaybackSpeed.execute(
                input = ChangePlaybackSpeed.Input.Decrease
            ).fold(onSuccess = {
                transition(state)
            }, onFailure = {
                transition(state, HubEvent.Error("Could not decrease playback speed: ${it.message}"))
            })

            is HubCommand.Playback.IncreasePlaybackSpeed -> changePlaybackSpeed.execute(
                input = ChangePlaybackSpeed.Input.Increase
            ).fold(onSuccess = {
                transition(state)
            }, onFailure = {
                transition(state, HubEvent.Error("Could not increase playback speed: ${it.message}"))
            })

            is HubCommand.Playback.ResetPlaybackSpeed -> changePlaybackSpeed.execute(
                input = ChangePlaybackSpeed.Input.Reset
            ).fold(onSuccess = {
                transition(state)
            }, onFailure = {
                transition(state, HubEvent.Error("Could not reset playback speed: ${it.message}"))
            })
        }
}