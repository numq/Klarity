package playlist.presentation

import feature.Reducer
import feature.Transition
import playlist.*

class PlaylistReducer(
    private val interactionReducer: PlaylistInteractionReducer,
    private val playbackReducer: PlaylistPlaybackReducer,
    private val previewReducer: PlaylistPreviewReducer,
    private val getPlaylist: GetPlaylist,
    private val addPlaylistItem: AddPlaylistItem,
    private val removePlaylistItem: RemovePlaylistItem,
    private val selectPlaylistItem: SelectPlaylistItem,
    private val previousPlaylistItem: PreviousPlaylistItem,
    private val nextPlaylistItem: NextPlaylistItem,
    private val changePlaylistMode: ChangePlaylistMode,
    private val changePlaylistShuffling: ChangePlaylistShuffling,
) : Reducer<PlaylistCommand, PlaylistState, PlaylistEvent> {
    override suspend fun reduce(
        state: PlaylistState, command: PlaylistCommand,
    ): Transition<PlaylistState, PlaylistEvent> = when (command) {
        is PlaylistCommand.Interaction -> interactionReducer.reduce(state, command)

        is PlaylistCommand.Playback -> playbackReducer.reduce(state, command)

        is PlaylistCommand.Preview -> previewReducer.reduce(state, command)

        is PlaylistCommand.GetPlaylist -> getPlaylist.execute(Unit).fold(onSuccess = { playlist ->
            transition(state, PlaylistEvent.HandlePlaylist(playlist = playlist))
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not get playlist: ${it.message}"))
        })

        is PlaylistCommand.UpdatePlaylist -> transition(state.copy(playlist = command.playlist))

        is PlaylistCommand.AddToPlaylist -> addPlaylistItem.execute(
            input = AddPlaylistItem.Input(location = command.location)
        ).fold(onSuccess = {
            transition(state.copy(isInputDialogVisible = false, isDragAndDropActive = false))
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not add to playlist: ${it.message}"))
        })

        is PlaylistCommand.RemoveFromPlaylist -> removePlaylistItem.execute(RemovePlaylistItem.Input(item = command.item))
            .fold(onSuccess = {
                transition(state.copy(isInputDialogVisible = false, isDragAndDropActive = false))
            }, onFailure = {
                transition(state, PlaylistEvent.Error("Could not remove item: ${it.message}"))
            })

        is PlaylistCommand.SelectItem -> selectPlaylistItem.execute(
            input = SelectPlaylistItem.Input(
                item = command.item,
                selectedPlaylistItem = state.playlist.selectedPlaylistItem
            )
        ).fold(onSuccess = {
            transition(state.copy(isInputDialogVisible = false, isDragAndDropActive = false))
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not select item: ${it.message}"))
        })

        is PlaylistCommand.Previous -> previousPlaylistItem.execute(input = Unit).fold(onSuccess = {
            transition(state.copy(isInputDialogVisible = false, isDragAndDropActive = false))
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not select previous item: ${it.message}"))
        })

        is PlaylistCommand.Next -> nextPlaylistItem.execute(input = Unit).fold(onSuccess = {
            transition(state.copy(isInputDialogVisible = false, isDragAndDropActive = false))
        }, onFailure = {
            transition(state, PlaylistEvent.Error("Could not select next item: ${it.message}"))
        })

        is PlaylistCommand.SetMode -> changePlaylistMode.execute(input = ChangePlaylistMode.Input(mode = command.mode))
            .fold(onSuccess = {
                transition(state)
            }, onFailure = {
                transition(state, PlaylistEvent.Error("Could not set playlist mode: ${it.message}"))
            })

        is PlaylistCommand.Shuffle -> changePlaylistShuffling.execute(input = ChangePlaylistShuffling.Input(isShuffled = !state.playlist.isShuffled))
            .fold(onSuccess = {
                transition(state)
            }, onFailure = {
                transition(state, PlaylistEvent.Error("Could not shuffle playlist: ${it.message}"))
            })

        is PlaylistCommand.CleanUp -> when {
            state.playlist.selectedPlaylistItem is SelectedPlaylistItem.Present -> playbackReducer.reduce(
                state, PlaylistCommand.Playback.Stop(item = state.playlist.selectedPlaylistItem.item)
            )

            else -> transition(state)
        }
    }
}