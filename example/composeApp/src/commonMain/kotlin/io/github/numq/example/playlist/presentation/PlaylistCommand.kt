package io.github.numq.example.playlist.presentation

import io.github.numq.example.item.Item
import io.github.numq.example.playlist.Playlist
import io.github.numq.example.playlist.PlaylistMode
import kotlin.time.Duration

sealed interface PlaylistCommand {
    sealed interface Interaction : PlaylistCommand {
        data object ShowOverlay : Interaction

        data object HideOverlay : Interaction

        data object ShowPlaylist : Interaction

        data object HidePlaylist : Interaction

        data object ShowFileChooser : Interaction

        data object HideFileChooser : Interaction

        data object ShowInputDialog : Interaction

        data object HideInputDialog : Interaction

        data object SetDragAndDropActive : Interaction

        data object SetDragAndDropInactive : Interaction
    }

    sealed interface Playback : PlaylistCommand {
        data class Play(val item: Item.Loaded) : Playback

        data class Pause(val item: Item.Loaded) : Playback

        data class Resume(val item: Item.Loaded) : Playback

        data class Stop(val item: Item.Loaded) : Playback

        data class SeekTo(val item: Item.Loaded, val timestamp: Duration) : Playback

        data class ChangeVolume(val volume: Float) : Playback

        data object ToggleMute : Playback

        data object DecreaseSpeed : Playback

        data object IncreaseSpeed : Playback

        data object ResetSpeed : Playback
    }

    sealed interface Preview : PlaylistCommand {
        data class GetTimestamp(val previewTimestamp: PreviewTimestamp?) : Preview
    }

    data object GetPlaylist : PlaylistCommand

    data class UpdatePlaylist(val playlist: Playlist) : PlaylistCommand

    data class AddToPlaylist(val location: String) : PlaylistCommand

    data class RemoveFromPlaylist(val item: Item) : PlaylistCommand

    data class SelectItem(val item: Item.Loaded) : PlaylistCommand

    data object Previous : PlaylistCommand

    data object Next : PlaylistCommand

    data class SetMode(val mode: PlaylistMode) : PlaylistCommand

    data object Shuffle : PlaylistCommand

    data object CleanUp : PlaylistCommand
}