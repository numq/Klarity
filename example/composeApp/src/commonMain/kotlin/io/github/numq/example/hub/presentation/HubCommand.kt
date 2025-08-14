package io.github.numq.example.hub.presentation

import io.github.numq.example.hub.Hub
import io.github.numq.example.item.Item

sealed interface HubCommand {
    sealed interface Interaction : HubCommand {
        data object ShowFileChooser : Interaction

        data object HideFileChooser : Interaction

        data object ShowInputDialog : Interaction

        data object HideInputDialog : Interaction

        data object SetDragAndDropActive : Interaction

        data object SetDragAndDropInactive : Interaction

        data class SetSliderStep(val step: Int) : Interaction
    }

    sealed interface Playback : HubCommand {
        data class StartPlayback(val item: Item.Loaded) : Playback

        data class StopPlayback(val item: Item.Loaded) : Playback

        data object DecreasePlaybackSpeed : Playback

        data object IncreasePlaybackSpeed : Playback

        data object ResetPlaybackSpeed : Playback
    }

    sealed interface Preview : HubCommand {
        data class StartPreview(val item: Item.Loaded) : Preview

        data class StopPreview(val item: Item.Loaded) : Preview
    }

    data object GetHub : HubCommand

    data class UpdateHub(val hub: Hub) : HubCommand

    data class AddToHub(val location: String) : HubCommand

    data class RemoveFromHub(val item: Item) : HubCommand

    data object CleanUp : HubCommand
}