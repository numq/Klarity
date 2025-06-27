package hub.presentation

import feature.Reducer
import feature.Transition
import hub.StartHubPreview
import hub.StopHubPreview

class HubPreviewReducer(
    private val startHubPreview: StartHubPreview, private val stopHubPreview: StopHubPreview,
) : Reducer<HubCommand.Preview, HubState, HubEvent> {
    override suspend fun reduce(state: HubState, command: HubCommand.Preview): Transition<HubState, HubEvent> =
        when (command) {
            is HubCommand.Preview.StartPreview -> startHubPreview.execute(
                input = StartHubPreview.Input(
                    item = command.item,
                    previewItem = state.hub.previewItem,
                    playbackItem = state.hub.playbackItem
                )
            ).fold(onSuccess = {
                transition(state)
            }, onFailure = {
                transition(state, HubEvent.Error("Could not start preview: ${it.message}"))
            })

            is HubCommand.Preview.StopPreview -> stopHubPreview.execute(
                input = StopHubPreview.Input(item = command.item, previewItem = state.hub.previewItem)
            ).fold(onSuccess = {
                transition(state)
            }, onFailure = {
                transition(state, HubEvent.Error("Could not stop preview: ${it.message}"))
            })
        }
}