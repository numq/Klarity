package playlist.presentation

import feature.Reducer
import feature.Transition

class PlaylistInteractionReducer : Reducer<PlaylistCommand.Interaction, PlaylistState, PlaylistEvent> {
    override suspend fun reduce(
        state: PlaylistState, command: PlaylistCommand.Interaction,
    ): Transition<PlaylistState, PlaylistEvent> = when (command) {
        is PlaylistCommand.Interaction.ShowOverlay -> transition(state.copy(isOverlayVisible = true))

        is PlaylistCommand.Interaction.HideOverlay -> transition(state.copy(isOverlayVisible = false))

        is PlaylistCommand.Interaction.ShowPlaylist -> transition(state.copy(isPlaylistVisible = true))

        is PlaylistCommand.Interaction.HidePlaylist -> transition(state.copy(isPlaylistVisible = false))

        is PlaylistCommand.Interaction.ShowFileChooser -> transition(state.copy(isFileChooserVisible = true))

        is PlaylistCommand.Interaction.HideFileChooser -> transition(state.copy(isFileChooserVisible = false))

        is PlaylistCommand.Interaction.ShowInputDialog -> transition(state.copy(isInputDialogVisible = true))

        is PlaylistCommand.Interaction.HideInputDialog -> transition(state.copy(isInputDialogVisible = false))

        is PlaylistCommand.Interaction.SetDragAndDropActive -> transition(
            state.copy(
                isInputDialogVisible = false,
                isDragAndDropActive = true
            )
        )

        is PlaylistCommand.Interaction.SetDragAndDropInactive -> transition(state.copy(isDragAndDropActive = false))
    }
}