package hub

import item.Item
import kotlinx.coroutines.flow.*
import playback.PlaybackService

interface HubRepository {
    val hub: Flow<Hub>

    suspend fun updatePreviewItem(item: Item.Loaded?): Result<Unit>

    suspend fun updatePlaybackItem(item: Item.Loaded?): Result<Unit>

    suspend fun addItem(item: Item.Loading): Result<Unit>

    suspend fun updateItem(updatedItem: Item): Result<Unit>

    suspend fun removeItem(item: Item): Result<Unit>

    class Implementation(
        playbackService: PlaybackService,
    ) : HubRepository {
        private val previewItem = MutableStateFlow<Item.Loaded?>(null)

        private val playbackItem = MutableStateFlow<Item.Loaded?>(null)

        private val items = MutableStateFlow(emptyList<Item>())

        override val hub = combine(
            items, previewItem, playbackItem, playbackService.state
        ) { items, previewItem, playbackItem, playbackState ->
            Hub(items = items, previewItem = previewItem, playbackItem = playbackItem, playbackState = playbackState)
        }

        override suspend fun updatePreviewItem(item: Item.Loaded?) = runCatching {
            previewItem.value = item
        }

        override suspend fun updatePlaybackItem(item: Item.Loaded?) = runCatching {
            playbackItem.value = item
        }

        override suspend fun addItem(item: Item.Loading) = runCatching {
            items.update { list -> list + item }
        }

        override suspend fun updateItem(updatedItem: Item) = runCatching {
            items.update { list ->
                list.map { item ->
                    when (item.id) {
                        updatedItem.id -> updatedItem

                        else -> item
                    }
                }
            }
        }

        override suspend fun removeItem(item: Item) = runCatching {
            items.update { items -> items.filterNot { it.id == item.id } }
        }
    }
}