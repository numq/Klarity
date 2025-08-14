package io.github.numq.example.playlist

import io.github.numq.example.item.Item
import io.github.numq.example.playback.PlaybackState

data class Playlist(
    val items: List<Item> = emptyList(),
    val selectedPlaylistItem: SelectedPlaylistItem = SelectedPlaylistItem.Absent(),
    val playbackState: PlaybackState = PlaybackState.Empty,
    val isShuffled: Boolean = false,
    val mode: PlaylistMode = PlaylistMode.NONE,
    val hasPrevious: Boolean = false,
    val hasNext: Boolean = false,
)