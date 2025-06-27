package playlist

import io.github.numq.klarity.queue.MediaQueue
import io.github.numq.klarity.queue.MediaQueueSelection
import io.github.numq.klarity.queue.RepeatMode
import item.Item
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine

interface PlaylistService {
    val playlist: Flow<Playlist>

    suspend fun addItem(item: Item.Loading): Result<Unit>

    suspend fun updateItem(item: Item): Result<Unit>

    suspend fun removeItem(item: Item): Result<Unit>

    suspend fun selectItem(item: Item.Loaded?): Result<Unit>

    suspend fun previous(): Result<Unit>

    suspend fun next(): Result<Unit>

    suspend fun setShuffled(isShuffled: Boolean): Result<Unit>

    suspend fun setMode(mode: PlaylistMode): Result<Unit>

    class Implementation(private val queue: MediaQueue<Item, Item.Loaded>) : PlaylistService {
        override val playlist = with(queue) {
            combine(
                selection, isShuffled, repeatMode, hasPrevious, hasNext
            ) { selection, isShuffled, repeatMode, hasPrevious, hasNext ->
                Playlist(
                    selectedPlaylistItem = when (selection) {
                        is MediaQueueSelection.Absent<Item.Loaded> -> SelectedPlaylistItem.Absent(updatedAt = selection.updatedAt)

                        is MediaQueueSelection.Present<Item.Loaded> -> SelectedPlaylistItem.Present(
                            item = selection.item, updatedAt = selection.updatedAt
                        )
                    },
                    isShuffled = isShuffled,
                    mode = PlaylistMode.valueOf(repeatMode.name),
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                )
            }.combine(items) { playlist, items ->
                playlist.copy(items = items)
            }
        }

        override suspend fun addItem(item: Item.Loading) = queue.add(item = item)

        override suspend fun updateItem(item: Item) = runCatching {
            val from = queue.items.value.firstOrNull { queueItem -> queueItem.id == item.id }

            checkNotNull(from) { "Item not found" }

            queue.replace(from = from, to = item).getOrThrow()
        }

        override suspend fun removeItem(item: Item) = queue.delete(item = item)

        override suspend fun selectItem(item: Item.Loaded?) = queue.select(item = item)

        override suspend fun previous() = queue.previous()

        override suspend fun next() = queue.next()

        override suspend fun setShuffled(isShuffled: Boolean) = queue.setShuffleEnabled(enabled = isShuffled)

        override suspend fun setMode(mode: PlaylistMode) = queue.setRepeatMode(
            repeatMode = RepeatMode.valueOf(mode.name)
        )
    }
}