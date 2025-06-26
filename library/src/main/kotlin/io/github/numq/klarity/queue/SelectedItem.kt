package io.github.numq.klarity.queue

import kotlin.time.Duration

/**
 * Represents the selection state of an item in the playlist-like queue.
 */
sealed interface SelectedItem {
    /**
     * Indicates that no item is currently selected in the queue.
     */
    data object Absent : SelectedItem

    /**
     * Represents an item that is currently selected in the queue.
     *
     * @param item the selected item
     * @param updatedAt the time in [Duration] when the item was last updated or selected
     */
    data class Present<out Item>(val item: Item, val updatedAt: Duration) : SelectedItem
}