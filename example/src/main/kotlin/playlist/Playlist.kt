package playlist

import item.Item
import playback.PlaybackState

data class Playlist(
    val items: List<Item> = emptyList(),
    val selectedPlaylistItem: SelectedPlaylistItem = SelectedPlaylistItem.Absent(),
    val playbackState: PlaybackState = PlaybackState.Empty,
    val isShuffled: Boolean = false,
    val mode: PlaylistMode = PlaylistMode.NONE,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)