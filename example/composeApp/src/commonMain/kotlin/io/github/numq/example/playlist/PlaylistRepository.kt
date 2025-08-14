package io.github.numq.example.playlist

import io.github.numq.example.item.Item
import io.github.numq.example.playback.PlaybackService
import kotlinx.coroutines.flow.*

interface PlaylistRepository {
    val playlist: Flow<Playlist>

    val previousSelectedPlaylistItem: StateFlow<SelectedPlaylistItem.Present?>

    suspend fun addItem(item: Item.Loading): Result<Unit>

    suspend fun updateItem(updatedItem: Item): Result<Unit>

    suspend fun removeItem(item: Item): Result<Unit>

    suspend fun updatePreviousSelectedPlaylistItem(selectedPlaylistItem: SelectedPlaylistItem.Present?): Result<Unit>

    class Implementation(
        playbackService: PlaybackService,
        private val playlistService: PlaylistService,
    ) : PlaylistRepository {
        private val _previousSelectedPlaylistItem = MutableStateFlow<SelectedPlaylistItem.Present?>(null)

        override val previousSelectedPlaylistItem = _previousSelectedPlaylistItem.asStateFlow()

        override val playlist = combine(playlistService.playlist, playbackService.state) { playlist, playbackState ->
            playlist.copy(playbackState = playbackState)
        }

        override suspend fun addItem(item: Item.Loading) = playlistService.addItem(item = item)

        override suspend fun updateItem(updatedItem: Item) = playlistService.updateItem(item = updatedItem)

        override suspend fun removeItem(item: Item) = playlistService.removeItem(item = item)

        override suspend fun updatePreviousSelectedPlaylistItem(
            selectedPlaylistItem: SelectedPlaylistItem.Present?,
        ) = runCatching {
            _previousSelectedPlaylistItem.value = selectedPlaylistItem
        }
    }
}