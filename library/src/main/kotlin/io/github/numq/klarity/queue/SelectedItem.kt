package io.github.numq.klarity.queue

/**
 * Represents the selection state of an item in the playlist-like queue.
 *
 * @param <Item> The type of the selected media item.
 */
sealed interface SelectedItem<out Item> {
    /**
     * Indicates that no item is currently selected in the queue.
     */
    data object Absent : SelectedItem<Nothing>

    /**
     * Represents an item that is currently selected in the queue.
     *
     * @param item the selected item
     * @param updatedAtNanos the time in nanoseconds when the item was last updated or selected
     */
    data class Present<out Item>(val item: Item, val updatedAtNanos: Long) : SelectedItem<Item>
}