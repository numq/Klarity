package playlist

import item.Item
import kotlin.time.Duration
import kotlin.time.Duration.Companion.nanoseconds

sealed interface SelectedPlaylistItem {
    val updatedAt: Duration

    data class Absent(override val updatedAt: Duration = System.nanoTime().nanoseconds) : SelectedPlaylistItem

    data class Present(
        val item: Item.Loaded,
        override val updatedAt: Duration = System.nanoTime().nanoseconds
    ) : SelectedPlaylistItem
}