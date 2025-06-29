package hub.presentation

import feature.Reducer
import feature.Transition
import feature.mergeEvents
import hub.AddHubItem
import hub.GetHub
import hub.RemoveHubItem

class HubReducer(
    private val interactionReducer: HubInteractionReducer,
    private val playbackReducer: HubPlaybackReducer,
    private val previewReducer: HubPreviewReducer,
    private val getHub: GetHub,
    private val addHubItem: AddHubItem,
    private val removeHubItem: RemoveHubItem
) : Reducer<HubCommand, HubState, HubEvent> {
    override suspend fun reduce(state: HubState, command: HubCommand): Transition<HubState, HubEvent> = when (command) {
        is HubCommand.Interaction -> interactionReducer.reduce(state, command)

        is HubCommand.Playback -> playbackReducer.reduce(state, command)

        is HubCommand.Preview -> previewReducer.reduce(state, command)

        is HubCommand.GetHub -> getHub.execute(Unit).fold(onSuccess = { hub ->
            transition(state, HubEvent.HandleHub(hub = hub))
        }, onFailure = {
            transition(state, HubEvent.Error("Could not get items: ${it.message}"))
        })

        is HubCommand.UpdateHub -> transition(state.copy(hub = command.hub))

        is HubCommand.AddToHub -> addHubItem.execute(input = AddHubItem.Input(location = command.location))
            .fold(onSuccess = {
                transition(state.copy(isInputDialogVisible = false, isDragAndDropActive = false))
            }, onFailure = {
                transition(state, HubEvent.Error("Could not add location: ${it.message}"))
            })

        is HubCommand.RemoveFromHub -> removeHubItem.execute(
            input = RemoveHubItem.Input(item = command.item, playbackItem = state.hub.playbackItem)
        ).fold(onSuccess = {
            transition(state.copy(hub = state.hub.copy(previewItem = state.hub.previewItem.takeIf { previewItem -> previewItem == command.item })))
        }, onFailure = {
            transition(state, HubEvent.Error("Could not remove item: ${it.message}"))
        })

        is HubCommand.CleanUp -> {
            val (updatedState, events) = if (state.hub.playbackItem != null) {
                playbackReducer.reduce(state, HubCommand.Playback.StopPlayback(item = state.hub.playbackItem))
            } else transition(state)

            if (updatedState.hub.previewItem != null) {
                previewReducer.reduce(
                    updatedState, HubCommand.Preview.StopPreview(item = updatedState.hub.previewItem)
                ).mergeEvents(events)
            } else transition(state)
        }
    }
}